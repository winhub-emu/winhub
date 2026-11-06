#!/usr/bin/env bash
# run_wine.sh <dlls dir> <tests dir> <feeder binary>
#
# The HERMES-XInput end-to-end test under Wine: for each case a fresh slot directory, the feeder
# publishing into it with the app's writer engine, and xinput_test.exe reading it through the DLLs
# placed next to it (forced native with WINEDLLOVERRIDES, the same override the app sets), plus
# GUI-subsystem children started without std handles (where the DLL's DllMain line must still reach
# Wine's stderr). CASES = "<dll dir>:<test dir>:<path mode> ...". Default (x86_64 runner, wine64 +
# wine32): x86_64 DLLs with every DOS path form (wine_get_dos_file_name, \\?\unix, Z:), and the i386
# DLLs under WoW64. On an aarch64 runner: "arm64x:aarch64:auto", the native ARM64 view of the ARM64X
# image in native ARM64 processes.
set -uo pipefail

DLLS="$(cd "${1:?dlls dir}" && pwd)"
TESTS="$(cd "${2:?tests dir}" && pwd)"
FEEDER="$(cd "$(dirname "${3:?feeder}")" && pwd)/$(basename "$3")"
: "${WINEPREFIX:=$HOME/.wine-hxi}"
# +debugstr only so the OutputDebugStringA log channel can be seen at all
export WINEPREFIX WINEDEBUG="${WINEDEBUG:--all,err+all,+debugstr}"
CASES="${CASES:-x86_64:x86_64:auto x86_64:x86_64:unix x86_64:x86_64:z i386:i386:auto}"
OVR="xinput1_1,xinput1_2,xinput1_3,xinput1_4,xinput9_1_0,xinputuap=n;mscoree,mshtml="
WINE="${WINE:-wine}"

if [ ! -f "$WINEPREFIX/system.reg" ]; then
  echo "== creating the Wine prefix"
  WINEDLLOVERRIDES="mscoree,mshtml=" timeout 600 "$WINE" wineboot -i >/dev/null 2>&1
  timeout 60 wineserver -w || true
fi
"$WINE" --version

# The forward target for the live-mode phases: this Wine's own builtin xinput1_4.dll, renamed, where the
# app puts it (system32 for the 64-bit / ARM64X build, syswow64 for i386).
fwd=0
for root in /usr/lib /usr/lib64 /usr/local/lib /opt; do
  [ -d "$root" ] || continue
  while IFS= read -r d; do
    case "$(basename "$d")" in
      x86_64-windows|aarch64-windows) dst="$WINEPREFIX/drive_c/windows/system32" ;;
      i386-windows) dst="$WINEPREFIX/drive_c/windows/syswow64" ;;
      *) continue ;;
    esac
    [ -f "$d/xinput1_4.dll" ] && [ -d "$dst" ] || continue
    cp "$d/xinput1_4.dll" "$dst/hermes_wine_xinput.dll"
    echo "forward copy: $d/xinput1_4.dll -> $dst/hermes_wine_xinput.dll"
    fwd=$((fwd + 1))
  done < <(find "$root" -maxdepth 5 -type d -name '*-windows' -path '*wine*' 2>/dev/null)
done
[ "$fwd" -gt 0 ] || { echo "no Wine builtin xinput1_4.dll found for the forward copy"; exit 1; }

fails=0
run_case() { # run_case <dll dir> <test dir> <path mode>
  local arch="$1" tarch="$2" mode="$3" d port rc frc via
  d="$(mktemp -d)"
  mkdir -p "$d/slots" "$d/bin"
  cp "$DLLS/$arch/"*.dll "$TESTS/$tarch/xinput_test.exe" "$TESTS/$tarch/xinput_gui.exe" "$TESTS/$tarch/xinput_byname.exe" "$d/bin/"
  port=$((40000 + RANDOM % 20000))
  echo
  echo "======== $arch DLLs, $tarch test programs, paths: $mode, rumble port $port"
  "$FEEDER" script "$d/slots" "$d/ctl" "$port" >"$d/feeder.log" 2>&1 &
  local fpid=$!
  for _ in $(seq 1 200); do [ -f "$d/ctl.ready" ] && break; sleep 0.05; done
  [ -f "$d/ctl.ready" ] || { echo "feeder did not start"; cat "$d/feeder.log"; fails=$((fails + 1)); return; }
  local ctl_dos="Z:$(echo "$d/ctl" | tr / '\\')"
  FAKE_EVDEV_MEMFD_PATHS="0=$d/slots/pad0;1=$d/slots/pad1;2=$d/slots/pad2" \
  HERMES_XINPUT_PATHMODE="$mode" HERMES_XINPUT_RUMBLE_PORT="$port" WINEDLLOVERRIDES="$OVR" \
    timeout 240 "$WINE" "$d/bin/xinput_test.exe" "$ctl_dos" 2>"$d/stderr.log"
  rc=$?
  # the test writes step 999 on its way out; make sure the feeder stops even if it did not
  [ "$rc" = 0 ] || printf '%08u %08u\n' 999 $((99999999 - 999)) >"$d/ctl"
  wait "$fpid"; frc=$?
  echo "---- feeder"
  cat "$d/feeder.log"
  echo "---- DLL diagnostics (Wine's stderr)"
  grep -a 'HERMES-XInput' "$d/stderr.log" | head -60 || true
  echo "---- GUI children without std handles: which log channel reached Wine's stderr"
  for via in wine_dbg stderr ods; do
    if grep -a 'loaded into .*xinput_gui.exe' "$d/stderr.log" | grep -aq "\[log via $via\]"; then
      echo "    DllMain line [log via $via]: in Wine's stderr"
    else
      echo "    DllMain line [log via $via]: missing"
    fi
  done
  echo "---- rumble diagnostics"
  for pat in 'winsock loaded for the first rumble or mode status' \
             'slot 0: first XInputSetState in this process .*low 40000, high 20000 -> ERROR_SUCCESS' \
             'slot 0: motors ON (low 40000, high 20000): datagram sent' \
             'slot 1: first XInputSetState in this process .*ERROR_DEVICE_NOT_CONNECTED'; do
    if grep -aq "$pat" "$d/stderr.log"; then echo "    found: $pat"; else echo "!!!! missing rumble diagnostic: $pat"; rc=1; fi
  done
  if ! grep -a 'loaded into .*xinput_gui.exe' "$d/stderr.log" | grep -aq '\[log via wine_dbg\]'; then
    echo "!!!! the default log channel (wine_dbg) did not reach Wine's stderr from a GUI process"
    rc=1
  fi
  if [ "$rc" != 0 ] || [ "$frc" != 0 ]; then
    echo "!!!! $arch/$mode failed: test exit $rc, feeder exit $frc"
    echo "---- full stderr (tail)"; tail -60 "$d/stderr.log"
    fails=$((fails + 1))
  else
    echo "==== $arch/$mode passed"
  fi
  timeout 30 wineserver -w || true
}

for c in $CASES; do
  IFS=: read -r a t m <<<"$c"
  run_case "$a" "$t" "$m"
done

echo
echo "== $fails case(s) failed"
[ "$fails" = 0 ]

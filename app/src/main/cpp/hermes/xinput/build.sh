#!/usr/bin/env bash
# HERMES-XInput: build the replacement XInput DLLs and their CI tests. Used by
# .github/workflows/_build.yml (DLLs into the APK assets) and .github/workflows/hermes-xinput.yml.
#
#   build.sh toolchain        fetch the pinned llvm-mingw into $LLVM_MINGW (default ~/.cache/...)
#   build.sh dlls <outdir>    <outdir>/{x86_64,i386,arm64x}/<dll>.dll for every XInput DLL name, each
#                             export table (both views of an ARM64X image), import list and machine
#                             type checked, plus <outdir>/manifest.json
#   build.sh tests <outdir>   <outdir>/{x86_64,i386,aarch64}/xinput_test.exe + xinput_gui.exe (Wine tests)
#   build.sh host <outdir>    <outdir>/host_test (native, cc) and the feeder (cargo, release)
#   build.sh wine-check <dlls dir> <root>...  our DLLs against the export tables of the Wine builtin
#                             XInput DLLs found under <root> (a superset of their names and of the
#                             ordinals programs import by), and every Wine module importing from an
#                             XInput DLL (joy.cpl, ...) resolved against ours (test/wine_check.py)
#
# The PE side uses bylaws/llvm-mingw (its arm64ec target does not predefine __x86_64__, unlike
# upstream's). One C source (src/xinput.c) is linked once per DLL name with that name's .def file
# (def/), so each DLL has exactly the exports and ordinals of the Windows DLL it stands in for.
# i386 .def files get stdcall decorations here and the link strips them (--kill-at).
#
# arm64x (system32 of an arm64ec Wine): the source is compiled for ARM64EC and for native ARM64 and
# both objects are linked into one ARM64X image (lld: -m arm64xpe = -machine:arm64x, the .def for the
# EC view, -defarm64native for the native view; each view gets its own CRT, the native one taken from
# the toolchain's own aarch64 link line). An x64 game loads the EC view, a native ARM64 process (Wine's
# own control.exe, explorer, ...) the ARM64 view; an ARM64EC-only image fails there with c000007b.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
LLVM_MINGW_VER=20250920
case "$(uname -m)" in
  aarch64|arm64)
    LLVM_MINGW_HOST=aarch64; HOST_SUFFIX=-aarch64
    LLVM_MINGW_SHA256=bce5cc755c613515fd44e1ee9523123d854103abae147571adb645450036274d ;;
  *)
    LLVM_MINGW_HOST=x86_64; HOST_SUFFIX=
    LLVM_MINGW_SHA256=8dd8c34fc051a50c2fae86015f35057f8aae93fe1e19b34537ef1269a8b4c772 ;;
esac
LLVM_MINGW_TAR="llvm-mingw-${LLVM_MINGW_VER}-ucrt-ubuntu-22.04-${LLVM_MINGW_HOST}.tar.xz"
LLVM_MINGW_URL="https://github.com/bylaws/llvm-mingw/releases/download/${LLVM_MINGW_VER}/${LLVM_MINGW_TAR}"
LLVM_MINGW="${LLVM_MINGW:-$HOME/.cache/llvm-mingw-bylaws-${LLVM_MINGW_VER}${HOST_SUFFIX}}"

DLLS="xinput1_1 xinput1_2 xinput1_3 xinput1_4 xinput9_1_0 xinputuap"
ARCHES="x86_64 i386 arm64x"

say() { printf '== hermes-xinput: %s\n' "$*"; }
die() { printf '::error::hermes-xinput: %s\n' "$*" >&2; exit 1; }

toolchain() {
  if [ ! -x "$LLVM_MINGW/bin/x86_64-w64-mingw32-clang" ]; then
    say "fetching $LLVM_MINGW_TAR"
    local tmp; tmp="$(mktemp -d)"
    curl -fsSL --retry 5 --retry-delay 5 -o "$tmp/t.tar.xz" "$LLVM_MINGW_URL"
    echo "$LLVM_MINGW_SHA256  $tmp/t.tar.xz" | sha256sum -c -
    mkdir -p "$LLVM_MINGW"
    tar -xJf "$tmp/t.tar.xz" -C "$LLVM_MINGW" --strip-components=1
    rm -rf "$tmp"
  fi
  export PATH="$LLVM_MINGW/bin:$PATH"
  for t in x86_64 i686 arm64ec aarch64; do
    command -v "$t-w64-mingw32-clang" >/dev/null || die "llvm-mingw has no $t-w64-mingw32-clang"
  done
  say "llvm-mingw $LLVM_MINGW_VER: $(x86_64-w64-mingw32-clang --version | head -1)"
}

cc_for() {
  case "$1" in
    x86_64) echo x86_64-w64-mingw32-clang ;;
    i386) echo i686-w64-mingw32-clang ;;
    arm64ec) echo arm64ec-w64-mingw32-clang ;;
    aarch64) echo aarch64-w64-mingw32-clang ;;
    *) die "unknown arch $1" ;;
  esac
}

# Extra flags per arch. i386 gets SSE2 so the reader's full fence is mfence (see hxi_core.h).
arch_flags() {
  case "$1" in
    i386) echo "-msse2 -mfpmath=sse" ;;
    *) echo "" ;;
  esac
}

# stdcall argument bytes of every export (i386 decoration).
I386_DECOR='DllMain:12 XInputGetState:8 XInputSetState:8 XInputGetStateEx:8 XInputGetCapabilities:12 XInputGetDSoundAudioDeviceGuids:12 XInputGetBatteryInformation:12 XInputGetKeystroke:12 XInputWaitForGuideButton:12 XInputEnable:4 XInputCancelGuideButtonWait:4 XInputPowerOffController:4 XInputGetAudioDeviceIds:20 XInputGetCapabilitiesEx:16'

def_for() { # def_for <arch> <dll> <tmpdir> -> path of the .def to link with
  local src="$HERE/def/$2.def"
  if [ "$1" != i386 ]; then echo "$src"; return; fi
  awk -v table="$I386_DECOR" 'BEGIN { n = split(table, a, " "); for (i = 1; i <= n; i++) { split(a[i], kv, ":"); m[kv[1]] = kv[2] } }
       ($1 in m) { sub($1, $1 "@" m[$1]) } { print }' "$src" > "$3/$2.def"
  echo "$3/$2.def"
}

# expected "<ordinal> <name|->" lines of a .def (NONAME exports have no name)
def_exports() {
  awk 'f && NF { ord = ""; for (i = 2; i <= NF; i++) if ($i ~ /^@[0-9]+$/) ord = substr($i, 2);
                 name = $1; for (i = 2; i <= NF; i++) if ($i == "NONAME") name = "-";
                 print (ord == "" ? "?" : ord), name }
       /^EXPORTS/ { f = 1 }' "$1" | sort
}

# actual "<ordinal> <name|->" lines of a DLL (the export address table also lists the unused
# ordinals between the numbered ones, with RVA 0: those are skipped). dll_exports <file> [main|hybrid]:
# llvm-readobj prints an ARM64X image's native view first, then its EC view inside "HybridObject {".
dll_exports() {
  llvm-readobj --coff-exports "$1" | awk -v view="${2:-main}" '
    /HybridObject \{/ { h = 1; next }
    (view == "main" && h) || (view == "hybrid" && !h) { next }
    /^ *Export \{/ { o = ""; n = "-"; r = "" }
    /^ *Ordinal:/ { o = $2 }
    /^ *Name:/ { if (NF >= 2) n = $2 }
    /^ *RVA:/ { r = $2 }
    /^ *\}/ { if (o != "" && r != "" && r != "0x0") print o, n; o = "" }' | sort
}

compare_exports() { # compare_exports <label> <file> <def> <view>
  local want got
  want="$(def_exports "$HERE/def/$3.def")"
  got="$(dll_exports "$2" "$4")"
  if [ "$want" != "$got" ]; then
    printf 'want:\n%s\ngot:\n%s\n' "$want" "$got"
    die "$1 exports differ from def/$3.def"
  fi
}

check_dll() { # check_dll <arch> <dll name> <file> <def>
  local arch="$1" name="$2" f="$3" def="$4" fmts fmt hyb machine lc imports bad
  fmts="$(llvm-readobj --file-headers "$f" | sed -n 's/^ *Format: //p')"
  fmt="$(sed -n 1p <<<"$fmts")"
  hyb="$(sed -n 2p <<<"$fmts")"
  case "$arch" in
    x86_64) [ "$fmt" = "COFF-x86-64" ] && [ -z "$hyb" ] || die "$f is $fmt${hyb:+ + $hyb}, want COFF-x86-64" ;;
    i386) [ "$fmt" = "COFF-i386" ] && [ -z "$hyb" ] || die "$f is $fmt${hyb:+ + $hyb}, want COFF-i386" ;;
    arm64x)
      # Native view: an ARM64 PE (what an ARM64 process maps). EC view: ARM64EC (what an x64 process
      # sees through the ARM64X dynamic relocations). Both need the CHPE metadata.
      machine="$(python3 -c 'import struct,sys; d=open(sys.argv[1],"rb").read(4096); o=struct.unpack_from("<I",d,0x3c)[0]; print(hex(struct.unpack_from("<H",d,o+4)[0]) if d[o:o+4]==b"PE\0\0" else "?")' "$f")"
      [ "$machine" = 0xaa64 ] || die "$f PE header machine is $machine, want 0xaa64 (ARM64)"
      [ "$hyb" = "COFF-ARM64EC" ] || die "$f has no ARM64EC hybrid view (formats: $(echo $fmts))"
      lc="$(llvm-readobj --coff-load-config "$f")"
      grep -q 'CHPEMetadata' <<<"$lc" || die "$f has no CHPE metadata"
      grep -q 'Arm64X' <<<"$lc" || die "$f has no ARM64X dynamic relocations"
      # One TLS directory serves both views and would list native ARM64 callbacks (see src/notls.c).
      llvm-readobj --file-headers "$f" | grep -q 'TLSTableRVA: 0x[1-9a-fA-F]' && die "$f has a TLS directory"
      fmt="$fmt/$hyb/$machine"
      ;;
  esac
  compare_exports "$arch/$name.dll" "$f" "$name" main
  [ "$arch" != arm64x ] || compare_exports "$arch/$name.dll (EC view)" "$f" "$name" hybrid
  imports="$(llvm-readobj --coff-imports "$f" | sed -n 's/^ *Name: //p' | tr 'A-Z' 'a-z' | sort -u)"
  bad="$(grep -vxE 'kernel32\.dll|ucrtbase\.dll|msvcrt\.dll|api-ms-win-crt-[a-z0-9-]+\.dll' <<<"$imports" || true)"
  [ -z "$bad" ] || die "$arch/$name.dll imports $(echo $bad) (only kernel32 and the C runtime are allowed)"
  grep -aq 'HERMES-XInput' "$f" || die "$arch/$name.dll lacks its HERMES-XInput marker"
  printf '     %-7s %-15s %6d bytes  %-24s imports: %s\n' "$arch" "$name.dll" "$(stat -c %s "$f")" "$fmt" "$(echo $imports)"
}

# The objects and archives the toolchain links into an aarch64 DLL (CRT start-up, mingw/ucrt/kernel32
# import libraries, compiler-rt), as absolute paths: the native view of an ARM64X image needs its own.
native_link_inputs() { # native_link_inputs <tmpdir>
  printf 'int x;\n' > "$1/empty.c"
  aarch64-w64-mingw32-clang -shared -o "$1/empty.dll" "$1/empty.c" -### 2>&1 | python3 -c '
import os, shlex, sys
line = [l for l in sys.stdin.read().splitlines() if "ld.lld" in l or "lld-link" in l]
if not line:
    sys.exit("no link line in the -### output")
toks = shlex.split(line[-1])
dirs = [t[2:] for t in toks if t.startswith("-L")]
out = []
for t in toks[1:]:
    if t.startswith("-l"):
        name = t[2:]
        hit = next((os.path.join(d, c) for d in dirs for c in ("lib%s.a" % name, "lib%s.dll.a" % name, "%s.lib" % name)
                    if os.path.isfile(os.path.join(d, c))), None)
        if not hit:
            sys.exit("cannot resolve -l%s in %s" % (name, dirs))
        out.append(hit)
    elif t.endswith((".o", ".a", ".obj", ".lib")) and os.path.isabs(t) and os.path.isfile(t):
        out.append(t)
seen = set()
print("\n".join(p for p in out if not (p in seen or seen.add(p))))
'
}

# notls-*.o (src/notls.c) come before the CRT so the image gets no TLS directory: an ARM64X image has
# one for both views and it would list native ARM64 callbacks, which an x64 process runs as x86.
link_arm64x() { # link_arm64x <out> <def> <ec obj> <native obj> <native inputs file> <ec notls obj> <native notls obj>
  local nat=()
  mapfile -t nat <"$5"
  arm64ec-w64-mingw32-clang -shared -s -Wl,--no-insert-timestamp -Wl,-marm64xpe "-Wl,--Xlink=-defarm64native:$2" \
    -o "$1" "$3" "$6" "$4" "$7" "$2" "${nat[@]}" -lkernel32
}

CFLAGS_COMMON="-std=gnu11 -O2 -Wall -Wextra -Werror -fno-strict-aliasing -I$HERE/src"

dlls() {
  local out="${1:?usage: build.sh dlls <outdir>}" tmp arch name cc def
  toolchain
  mkdir -p "$out"; out="$(cd "$out" && pwd)"
  tmp="$(mktemp -d)"
  for arch in $ARCHES; do
    mkdir -p "$out/$arch"
    if [ "$arch" = arm64x ]; then
      arm64ec-w64-mingw32-clang $CFLAGS_COMMON -DHXI_HYBRID=1 -c "$HERE/src/xinput.c" -o "$tmp/xinput-arm64ec.o"
      aarch64-w64-mingw32-clang $CFLAGS_COMMON -DHXI_HYBRID=1 -c "$HERE/src/xinput.c" -o "$tmp/xinput-aarch64.o"
      arm64ec-w64-mingw32-clang $CFLAGS_COMMON -c "$HERE/src/notls.c" -o "$tmp/notls-arm64ec.o"
      aarch64-w64-mingw32-clang $CFLAGS_COMMON -c "$HERE/src/notls.c" -o "$tmp/notls-aarch64.o"
      native_link_inputs "$tmp" >"$tmp/native-inputs"
      say "native view links with: $(xargs -n1 basename <"$tmp/native-inputs" | tr '\n' ' ')"
      for name in $DLLS; do
        link_arm64x "$out/$arch/$name.dll" "$HERE/def/$name.def" "$tmp/xinput-arm64ec.o" "$tmp/xinput-aarch64.o" "$tmp/native-inputs" \
          "$tmp/notls-arm64ec.o" "$tmp/notls-aarch64.o"
        check_dll "$arch" "$name" "$out/$arch/$name.dll" "$HERE/def/$name.def"
      done
      continue
    fi
    cc="$(cc_for "$arch")"
    # shellcheck disable=SC2046
    "$cc" $CFLAGS_COMMON $(arch_flags "$arch") -c "$HERE/src/xinput.c" -o "$tmp/xinput-$arch.o"
    for name in $DLLS; do
      def="$(def_for "$arch" "$name" "$tmp")"
      local kill=""; [ "$arch" = i386 ] && kill="-Wl,--kill-at"
      "$cc" -shared -s -Wl,--no-insert-timestamp $kill -o "$out/$arch/$name.dll" "$tmp/xinput-$arch.o" "$def" -lkernel32
      check_dll "$arch" "$name" "$out/$arch/$name.dll" "$def"
    done
  done
  rm -rf "$tmp"
  python3 - "$out" "$DLLS" "$ARCHES" <<'PY'
import hashlib, json, os, sys
out, dlls, arches = sys.argv[1], sys.argv[2].split(), sys.argv[3].split()
files = []
for arch in arches:
    for name in dlls:
        p = os.path.join(out, arch, name + ".dll")
        files.append({"arch": arch, "name": name + ".dll", "asset": f"{arch}/{name}.dll",
                      "sha256": hashlib.sha256(open(p, "rb").read()).hexdigest()})
tag = hashlib.sha256("".join(f["sha256"] for f in files).encode()).hexdigest()[:12]
json.dump({"name": "HERMES-XInput", "version": "1.3+" + tag, "dlls": dlls, "arches": arches, "files": files},
          open(os.path.join(out, "manifest.json"), "w"), indent=1)
print(f"== hermes-xinput: manifest.json: {len(files)} dlls, version 1.3+{tag}")
PY
}

tests() {
  local out="${1:?usage: build.sh tests <outdir>}" arch cc tmp m dll k
  toolchain
  mkdir -p "$out"
  tmp="$(mktemp -d)"
  for arch in x86_64 i386 aarch64; do
    cc="$(cc_for "$arch")"
    mkdir -p "$out/$arch" "$tmp/$arch"
    # import libraries made from our .def files, so xinput_byname.exe imports the extended functions
    # BY NAME (an i386 one from the decorated .def, with the decorations killed like the DLL's)
    case "$arch" in x86_64) m=i386:x86-64 ;; i386) m=i386 ;; aarch64) m=arm64 ;; esac
    for dll in xinput1_3 xinput1_4; do
      k=""; [ "$arch" = i386 ] && k="-k"
      llvm-dlltool -m "$m" -d "$(def_for "$arch" "$dll" "$tmp/$arch")" -D "$dll.dll" -l "$tmp/$arch/lib$dll.a" $k
    done
    # shellcheck disable=SC2046
    "$cc" $CFLAGS_COMMON $(arch_flags "$arch") -o "$out/$arch/xinput_byname.exe" "$HERE/test/xinput_byname.c" \
      -L"$tmp/$arch" -lxinput1_3 -lxinput1_4
    llvm-readobj --coff-imports "$out/$arch/xinput_byname.exe" | grep -E 'Name: xinput|Symbol: XInput' | sed 's/^/     /'
    # shellcheck disable=SC2046
    "$cc" $CFLAGS_COMMON $(arch_flags "$arch") -o "$out/$arch/xinput_test.exe" "$HERE/test/xinput_test.c"
    # a GUI-subsystem child the test starts without std handles, like a game started by explorer
    # shellcheck disable=SC2046
    "$cc" $CFLAGS_COMMON $(arch_flags "$arch") -mwindows -o "$out/$arch/xinput_gui.exe" "$HERE/test/xinput_gui.c"
    say "$arch: xinput_test.exe $(llvm-readobj --file-headers "$out/$arch/xinput_test.exe" | sed -n 's/^Format: //p' | head -1)," \
        "xinput_gui.exe $(llvm-readobj --file-headers "$out/$arch/xinput_gui.exe" | sed -n 's/^ *Subsystem: //p' | head -1)"
  done
  rm -rf "$tmp"
}

host() {
  local out="${1:?usage: build.sh host <outdir>}"
  mkdir -p "$out"
  "${CC:-cc}" -std=gnu11 -O2 -Wall -Wextra -Werror -pthread -o "$out/host_test" "$HERE/test/host_test.c"
  (cd "$HERE/feeder" && cargo build --release)
  cp "$HERE/feeder/target/release/hermes-xinput-feeder" "$out/"
  say "host_test + feeder built for $(uname -m)"
}

wine_check() {
  local ours="${1:?usage: build.sh wine-check <dlls dir> <root>...}"
  shift
  [ "$#" -gt 0 ] || die "usage: build.sh wine-check <dlls dir> <root>..."
  toolchain
  python3 -I "$HERE/test/wine_check.py" "$(cd "$ours" && pwd)" "$@"
}

case "${1:-}" in
  toolchain) toolchain ;;
  wine-check) shift; wine_check "$@" ;;
  dlls) dlls "${2:-}" ;;
  tests) tests "${2:-}" ;;
  host) host "${2:-}" ;;
  *) die "usage: $0 toolchain | dlls <outdir> | tests <outdir> | host <outdir> | wine-check <dlls dir> <root>..." ;;
esac

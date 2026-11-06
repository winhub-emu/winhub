#!/usr/bin/env bash
# Build and verify the HERMES transport crate. Used by .github/workflows/_build.yml (before the
# Gradle/CMake build) and by .github/workflows/hermes-check.yml.
#
#   build.sh test     host unit + multi-process scenario tests (cargo test, blocking)
#   build.sh loom     loom model check of the publish/read protocol
#   build.sh android  libhermes_guest.a + libhermes.so for aarch64-linux-android, link probe
#                     (needs NDK_BIN = <ndk>/toolchains/llvm/prebuilt/linux-x86_64/bin)
#   build.sh glibc    libhermes_guest.a for aarch64-unknown-linux-gnu, link probe
#                     (needs aarch64-linux-gnu-gcc and/or -g++; NDK_BIN optional, for llvm-ar)
#
# The probes link a shared object referencing every hermes_* function, the same way the
# interposers link the archive, and fail the build if it needs anything beyond libc/libdl/
# pthread (liblog on Android), imports an unwinder, or exports any symbol of the library.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

say() { printf '== hermes: %s\n' "$*"; }
die() { printf '::error::hermes: %s\n' "$*" >&2; exit 1; }

ar_tool() {
  if [ -n "${NDK_BIN:-}" ] && [ -x "$NDK_BIN/llvm-ar" ]; then echo "$NDK_BIN/llvm-ar"
  elif command -v aarch64-linux-gnu-ar >/dev/null; then echo aarch64-linux-gnu-ar
  else die "no archiver (llvm-ar or aarch64-linux-gnu-ar) found"; fi
}

# The staticlib carries its own copy of Rust's compiler builtins (Rust objects named after the
# crate, plus compiler-rt C objects named "<16 hex>-<name>.o"). Drop them, so the interposer's link
# resolves those helpers from its own toolchain runtime exactly as before, and the archive
# contributes nothing but the transport itself. (Here-strings, not "| grep -q": under pipefail an
# early grep exit kills the writer of a long listing with SIGPIPE.)
BUILTINS_RE='compiler_builtins|^[0-9a-f]{16}-'
strip_builtins() {
  local a="$1" ar members victims i=0
  ar="$(ar_tool)"
  while :; do
    members="$("$ar" t "$a")"
    victims="$(grep -E "$BUILTINS_RE" <<<"$members" || true)"
    [ -n "$victims" ] || break
    xargs -n 200 "$ar" d "$a" <<<"$victims"
    i=$((i + 1)); [ "$i" -lt 20 ] || die "could not strip the builtins from $a"
  done
  "$ar" s "$a" 2>/dev/null || true  # deleting already refreshes the index; this is belt and braces
  members="$("$ar" t "$a")"
  say "archive members of $(basename "$(dirname "$(dirname "$a")")")/$(basename "$a") after stripping:"
  sed 's/^/     /' <<<"$members"
  if grep -qvE '^hermes_(guest|core)-' <<<"$members"; then
    die "unexpected archive members left in $a"
  fi
}

# list_undefined <nm> <archive>: what the archive needs from the final link.
list_undefined() {
  say "undefined symbols the archive needs from the link:"
  "$1" -u "$2" | awk '$1 == "U" {print $2}' | sort -u | tr '\n' ' '; echo
}

# check_probe <readelf> <nm> <so> <allowed NEEDED...>
check_probe() {
  local readelf="$1" nm="$2" so="$3"; shift 3
  local need; need="$("$readelf" -d "$so" | sed -n 's/.*NEEDED.*\[\(.*\)\]/\1/p')"
  say "$(basename "$so") NEEDED: $(echo $need)"
  local n ok a
  for n in $need; do
    ok=0
    for a in "$@"; do [ "$n" = "$a" ] && ok=1; done
    [ "$ok" = 1 ] || die "$(basename "$so") needs $n (allowed: $*)"
  done
  local exports; exports="$("$nm" -D --defined-only "$so" | awk '{print $NF}')"
  say "$(basename "$so") exports: $(echo $exports)"
  local bad; bad="$(grep -E '^(hermes_|rust_|__rust|_ZN|_R[A-Z0-9])' <<<"$exports" | grep -vx 'hermes_linkprobe_entry' || true)"
  [ -z "$bad" ] || die "$(basename "$so") exports library symbols: $bad"
  grep -qx 'hermes_linkprobe_entry' <<<"$exports" || die "probe entry not exported"
  local imports; imports="$("$nm" -D --undefined-only "$so" | awk '{print $NF}')"
  say "$(basename "$so") imports: $(echo $imports)"
  if grep -qE '^(_Unwind_|__gcc_personality|rust_eh_personality)' <<<"$imports"; then
    die "$(basename "$so") imports unwinder symbols"
  fi
}

case "${1:-}" in
test)
  say "host tests"
  cargo test --workspace --no-fail-fast -- --test-threads=1
  ;;
loom)
  say "loom model check"
  (cd loom && LOOM_MAX_PREEMPTIONS="${LOOM_MAX_PREEMPTIONS:-2}" cargo test --release --no-fail-fast)
  ;;
android)
  : "${NDK_BIN:?NDK_BIN must point at <ndk>/toolchains/llvm/prebuilt/linux-x86_64/bin}"
  CC="$NDK_BIN/aarch64-linux-android26-clang"
  [ -x "$CC" ] || die "NDK clang missing: $CC"
  export CC_aarch64_linux_android="$CC"
  export AR_aarch64_linux_android="$NDK_BIN/llvm-ar"
  export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$CC"
  say "guest static library + JNI writer (aarch64-linux-android)"
  cargo build --release --target aarch64-linux-android -p hermes-guest -p hermes-jni
  A=target/aarch64-linux-android/release/libhermes_guest.a
  SO=target/aarch64-linux-android/release/libhermes.so
  test -s "$A" && test -s "$SO"
  strip_builtins "$A"
  list_undefined "$NDK_BIN/llvm-nm" "$A"
  say "link probe (bionic, as libfakeinput.so links it)"
  "$CC" -shared -fPIC -O2 -Wall -Wextra -Werror -fvisibility=hidden -Iinclude \
    -Wl,--no-undefined -Wl,--exclude-libs,libhermes_guest.a \
    -o target/probe-android.so ci/linkprobe.c "$A" -llog -ldl
  "$NDK_BIN/clang++" --target=aarch64-linux-android26 -std=c++17 -Wall -Wextra -Werror -fsyntax-only \
    -x c++ -Iinclude ci/linkprobe.c
  check_probe "$NDK_BIN/llvm-readelf" "$NDK_BIN/llvm-nm" target/probe-android.so libc.so libdl.so liblog.so libm.so
  say "JNI writer exports"
  JNI="$("$NDK_BIN/llvm-nm" -D --defined-only "$SO" | awk '{print $NF}')"
  for f in Prepare Release Open Publish SetOverlay Reset Unplug Stat GameFrame \
           SetSlotIdentity ClearSlotIdentity SetSessionControl GetControlGen SetFfMode; do
    grep -qx "Java_com_winhub_hernandez_inputcontrols_HermesWriter_native$f" <<<"$JNI" \
      || die "libhermes.so does not export native$f"
  done
  if grep -q '^hermes_' <<<"$JNI"; then die "libhermes.so exports guest symbols"; fi
  "$NDK_BIN/llvm-readelf" -d "$SO" | grep -E 'NEEDED|SONAME' | sed 's/^/     /'
  ls -l "$A" "$SO"
  ;;
glibc)
  say "guest static library (aarch64-unknown-linux-gnu)"
  cargo build --release --target aarch64-unknown-linux-gnu -p hermes-guest
  A=target/aarch64-unknown-linux-gnu/release/libhermes_guest.a
  test -s "$A"
  strip_builtins "$A"
  NM=aarch64-linux-gnu-nm
  if [ -n "${NDK_BIN:-}" ] && [ -x "$NDK_BIN/llvm-nm" ]; then NM="$NDK_BIN/llvm-nm"; fi
  if command -v "$NM" >/dev/null; then list_undefined "$NM" "$A"; fi
  probed=0
  if command -v aarch64-linux-gnu-gcc >/dev/null; then
    say "link probe (glibc, C)"
    aarch64-linux-gnu-gcc -shared -fPIC -O2 -Wall -Wextra -Werror -fvisibility=hidden -pthread -static-libgcc \
      -Iinclude -Wl,--no-undefined -Wl,--exclude-libs,ALL \
      -o target/probe-glibc.so ci/linkprobe.c "$A" -ldl
    check_probe aarch64-linux-gnu-readelf aarch64-linux-gnu-nm target/probe-glibc.so \
      libc.so.6 libdl.so.2 libpthread.so.0 ld-linux-aarch64.so.1
    probed=1
  fi
  if command -v aarch64-linux-gnu-g++ >/dev/null; then
    say "link probe (glibc, C++ with static C++ runtime, as fakeinput_steam.cpp is built)"
    aarch64-linux-gnu-g++ -shared -fPIC -O2 -Wall -Wextra -Werror -fvisibility=hidden -pthread -std=c++17 \
      -static-libstdc++ -static-libgcc -Iinclude -Wl,--no-undefined -Wl,--exclude-libs,ALL \
      -x c++ -o target/probe-glibc-cxx.so ci/linkprobe.c -x none "$A" -ldl
    check_probe aarch64-linux-gnu-readelf aarch64-linux-gnu-nm target/probe-glibc-cxx.so \
      libc.so.6 libdl.so.2 libpthread.so.0 libm.so.6 ld-linux-aarch64.so.1
    probed=1
  fi
  [ "$probed" = 1 ] || die "no aarch64-linux-gnu compiler for the glibc link probe"
  ls -l "$A"
  ;;
*)
  die "usage: $0 test|loom|android|glibc"
  ;;
esac

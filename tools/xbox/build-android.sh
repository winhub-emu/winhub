#!/usr/bin/env bash
# Build the Xbox backend (Xodus CLI + account service) for Android (aarch64-linux-android, bionic),
# so it runs as a plain child process of the app: no Linux runtime, no proot.
#
# Source recipe, pinned revisions and patches are DroidDeck's (tools/xbox from Droid-Deck/DroidDeck,
# GPL-3.0): prepare.py fetches Xodus + Xodus Nexus at the locked commits, verifies every patch's
# sha256 and applies them plus DroidDeck's credential / launch-view / library patches.
#
# Output ($1, default build-xbox/out):
#   bin/libxodus_cli.so, bin/libxodus_service.so   the two executables, named lib*.so so they ship in
#                                                 jniLibs and are extracted to nativeLibraryDir
#   windows/*.dll                                 the five Xodus Nexus Windows modules (runtime.py)
#   LICENSE.*, *.lock.json, version
set -euo pipefail
repo=$(cd "$(dirname "$0")/../.." && pwd)
cache=${XBOX_BUILD_DIR:-"$repo/build-xbox"}
out=${1:-"$cache/out"}
api=${XBOX_ANDROID_API:-30}
mkdir -p "$cache" "$out/bin" "$out/windows"
cache=$(cd "$cache" && pwd); out=$(cd "$out" && pwd)

ndk=${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_ROOT:-}}}
test -d "$ndk" || { echo "no Android NDK (ANDROID_NDK_HOME)" >&2; exit 1; }
tc="$ndk/toolchains/llvm/prebuilt/linux-x86_64"
export ANDROID_NDK_ROOT="$ndk" ANDROID_NDK_HOME="$ndk" ANDROID_NDK="$ndk"
export PATH="$tc/bin:$PATH"

# protoc for prost-build (the distro one is too old); pinned by sha256.
if ! [ -x "$cache/protoc/bin/protoc" ]; then
    curl -fsSL --retry 5 --retry-all-errors -o "$cache/protoc.zip" \
        https://github.com/protocolbuffers/protobuf/releases/download/v29.3/protoc-29.3-linux-x86_64.zip
    echo "3e866620c5be27664f3d2fa2d656b5f3e09b5152b42f1bedbf427b333e90021a  $cache/protoc.zip" | sha256sum -c -
    rm -rf "$cache/protoc" && mkdir -p "$cache/protoc" && unzip -q "$cache/protoc.zip" -d "$cache/protoc"
fi
export PROTOC="$cache/protoc/bin/protoc"

# OpenSSL for openssl-sys (native-tls), static, for Android; pinned by sha256.
ossl="$cache/openssl-android-$api"
if ! [ -f "$ossl/lib/libssl.a" ]; then
    curl -fsSL --retry 5 --retry-all-errors -o "$cache/openssl.tgz" \
        https://github.com/openssl/openssl/releases/download/openssl-3.0.15/openssl-3.0.15.tar.gz
    echo "23c666d0edf20f14249b3d8f0368acaee9ab585b09e1de82107c66e1f3ec9533  $cache/openssl.tgz" | sha256sum -c -
    rm -rf "$cache/openssl-src" && mkdir -p "$cache/openssl-src"
    tar -xzf "$cache/openssl.tgz" -C "$cache/openssl-src" --strip-components=1
    (cd "$cache/openssl-src" && ./Configure android-arm64 -D__ANDROID_API__="$api" no-shared no-tests \
        --prefix="$ossl" --libdir=lib && make -j"$(nproc)" build_libs >/dev/null && make install_dev >/dev/null)
fi
export AARCH64_LINUX_ANDROID_OPENSSL_DIR="$ossl" AARCH64_LINUX_ANDROID_OPENSSL_STATIC=1 OPENSSL_STATIC=1

source_dir=$(python3 "$repo/tools/xbox/prepare.py" "$cache")
rust_version=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["rust"])' "$repo/tools/xbox/backend.lock.json")
rustup toolchain install "$rust_version" --profile minimal --target aarch64-linux-android
export RUSTUP_TOOLCHAIN=$rust_version
export CARGO_TARGET_DIR=${CARGO_TARGET_DIR:-"$cache/target"}
export CC_aarch64_linux_android="$tc/bin/aarch64-linux-android$api-clang"
export CXX_aarch64_linux_android="$tc/bin/aarch64-linux-android$api-clang++"
export AR_aarch64_linux_android="$tc/bin/llvm-ar"
export RANLIB_aarch64_linux_android="$tc/bin/llvm-ranlib"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$tc/bin/aarch64-linux-android$api-clang"
# The NDK's CMake toolchain reads ANDROID_ABI / ANDROID_PLATFORM as CMake variables (not env vars) and
# otherwise defaults to 32-bit ARM; a wrapper sets them for every cmake-built -sys crate (libz-ng-sys …).
cat > "$cache/android-arm64.toolchain.cmake" <<CMAKE
set(ANDROID_ABI arm64-v8a)
set(ANDROID_PLATFORM android-$api)
include("$ndk/build/cmake/android.toolchain.cmake")
CMAKE
export CMAKE_TOOLCHAIN_FILE_aarch64_linux_android="$cache/android-arm64.toolchain.cmake"
export ANDROID_ABI=arm64-v8a ANDROID_PLATFORM="android-$api"
# reqwest 0.13's default TLS verifier on Android needs the Java platform (JNI) and panics in a plain
# executable; tools/xbox/android-verifier replaces it (same crate name and version) via [patch.crates-io].
# This changes Cargo.lock for that one entry, so the build below runs without --locked.
rm -rf "$source_dir/vendor-android" && mkdir -p "$source_dir/vendor-android"
cp -r "$repo/tools/xbox/android-verifier" "$source_dir/vendor-android/rustls-platform-verifier"
python3 - "$source_dir/Cargo.toml" <<'PY'
import sys
p = sys.argv[1]; s = open(p).read()
line = 'rustls-platform-verifier = { path = "vendor-android/rustls-platform-verifier" }'
if line not in s:
    s = s.replace('[patch.crates-io]', '[patch.crates-io]\n' + line, 1) if '[patch.crates-io]' in s else s + '\n[patch.crates-io]\n' + line + '\n'
    open(p, 'w').write(s)
PY
cd "$source_dir"
# libz-ng-sys builds with cmake and does not rebuild when the toolchain file changes; a cached copy from
# before the arm64 toolchain wrapper is the wrong architecture. Clean it once per toolchain revision.
if [ ! -f "$CARGO_TARGET_DIR/.cmake-toolchain-v2" ]; then
    cargo clean --release --target aarch64-linux-android -p libz-ng-sys || true
    mkdir -p "$CARGO_TARGET_DIR" && touch "$CARGO_TARGET_DIR/.cmake-toolchain-v2"
fi
cargo build --release --no-default-features --target aarch64-linux-android -p xodus-cli -p xodus-service
grep -A2 'name = "rustls-platform-verifier"' Cargo.lock

for binary in xodus-cli xodus-service; do
    so="lib${binary//-/_}.so"
    install -m755 "$CARGO_TARGET_DIR/aarch64-linux-android/release/$binary" "$out/bin/$so"
    "$tc/bin/llvm-strip" --strip-unneeded "$out/bin/$so"
    "$tc/bin/llvm-readelf" -d "$out/bin/$so" | grep -E 'NEEDED|INTERP' || true
done
python3 "$repo/tools/xbox/runtime.py" "$cache/runtime" "$out/windows"
for f in LICENSE.xodus LICENSE.nexus LICENSE.LGPL-2.1 LICENSE.nghttp2 backend.lock.json runtime.lock.json; do
    install -m644 "$repo/tools/xbox/$f" "$out/$f"
done
python3 - "$out" <<'PY'
import hashlib,json,pathlib,sys
out=pathlib.Path(sys.argv[1])
files={str(p.relative_to(out)):hashlib.sha256(p.read_bytes()).hexdigest() for p in out.rglob('*') if p.is_file() and p.name!='version'}
(out/'version').write_text(hashlib.sha256(json.dumps(files,sort_keys=True).encode()).hexdigest()+'\n')
print(json.dumps(files,indent=1))
PY
# Corresponding source for the GPL binaries.
tar -cJf "$cache/xodus-bannerlator-source.tar.xz" --exclude=.git --exclude=target -C "$source_dir" . -C "$repo" tools/xbox
ls -l "$out/bin" "$out/windows"

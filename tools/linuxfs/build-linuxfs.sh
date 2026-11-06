#!/bin/bash
# Assembles the Linux runtime (files/linuxfs on the device) from Arch Linux ARM packages:
# the base rootfs, gamescope with Xwayland and Mesa, a file manager, and the Bannerlator session
# scripts from overlay/. No Valve software is included; bannerlator-steam-install fetches the
# native arm64 Steam client from Valve at first use.
#
#   tools/linuxfs/build-linuxfs.sh <work dir> <output.tar.zst>
#
# Needs curl, tar, zstd, ar, python3, proot, qemu-aarch64-static, aarch64-linux-gnu-gcc/g++, meson >= 1.5, ninja,
# and cargo with the aarch64-unknown-linux-gnu target (the controller-input transport, hermes).
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
work=${1:?work dir}
out=${2:?output tarball}
# The script cds into the work dir below; a relative output path must be pinned before that.
case $out in /*) ;; *) out=$PWD/$out ;; esac
mkdir -p "$(dirname "$out")"
mirror=http://mirror.archlinuxarm.org/aarch64
base_url=http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz
seeds=(gamescope mesa vulkan-freedreno xorg-xwayland xorg-xhost xorg-xrandr vulkan-tools wayland-utils
  mesa-utils foot pcmanfm unzip dbus libpulse nss libnm curl ca-certificates fontconfig freetype2
  bash coreutils grep sed gawk which findutils glib2 libglvnd ibus libxcomposite libxdamage libxrandr
  wayland wayland-protocols libxcb libxshmfence xkeyboard-config xorg-xkbcomp python
  libxtst libxi ttf-dejavu openal libvdpau lsof)

mkdir -p "$work/db" "$work/pkgs" "$work/rootfs"
cd "$work"

for repo in core extra alarm; do
  [ -s "db/$repo.db" ] || curl -fsSL --retry 6 --retry-delay 5 --retry-all-errors -o "db/$repo.db" "$mirror/$repo/$repo.db"
  mkdir -p "db/x_$repo"
  tar -xzf "db/$repo.db" -C "db/x_$repo"
done

# The package closure over the repository databases. Arch Linux ARM keeps %DEPENDS% in a
# separate `depends` file, and several dependencies are virtual names (libseat, sdl2,
# xorg-server-xwayland) satisfied through %PROVIDES%.
python3 - "${seeds[@]}" > pkglist.txt <<'PY'
import os, sys, collections
pkgs, provides = {}, collections.defaultdict(list)
def strip(d):
    for op in (">=", "<=", "==", ">", "<", "="):
        if op in d: return d.split(op)[0]
    return d
for repo in ("core", "extra", "alarm"):
    base = os.path.join("db", "x_" + repo)
    for entry in os.listdir(base):
        fields, key = {}, None
        for name in ("desc", "depends"):
            path = os.path.join(base, entry, name)
            if not os.path.exists(path): continue
            for line in open(path, encoding="utf-8", errors="replace"):
                line = line.rstrip("\n")
                if line.startswith("%") and line.endswith("%"): key = line.strip("%"); fields[key] = []
                elif line == "": key = None
                elif key: fields[key].append(line)
        n = fields.get("NAME", [None])[0]
        if not n: continue
        rec = {"repo": repo, "file": fields["FILENAME"][0],
               "depends": [strip(d) for d in fields.get("DEPENDS", [])],
               "provides": [strip(p) for p in fields.get("PROVIDES", [])]}
        pkgs[n] = rec
        provides[n].append(n)
        for p in rec["provides"]: provides[p].append(n)
seen, queue, missing = set(), list(sys.argv[1:]), []
while queue:
    want = queue.pop()
    real = want if want in pkgs else (provides.get(want) or [None])[0]
    if real is None: missing.append(want); continue
    if real in seen: continue
    seen.add(real)
    queue.extend(pkgs[real]["depends"])
if missing: sys.exit("unresolved: " + " ".join(missing))
for n in sorted(seen): print(pkgs[n]["repo"] + "/" + pkgs[n]["file"])
PY
echo "$(wc -l < pkglist.txt) packages"

while read -r entry; do
  file=${entry#*/}
  # A mirror error page is not a package; fetch again rather than fail at extraction.
  if ! tar -tf "pkgs/$file" >/dev/null 2>&1; then
    rm -f "pkgs/$file"
    curl -fsSL --retry 6 --retry-delay 5 --retry-all-errors -o "pkgs/$file" "$mirror/$entry"
    tar -tf "pkgs/$file" >/dev/null
  fi
done < pkglist.txt

[ -s base.tar.gz ] || curl -fsSL --retry 6 --retry-delay 5 --retry-all-errors -o base.tar.gz "$base_url"

rm -rf rootfs && mkdir rootfs
# The base tarball is owned by root:root with device nodes; extracted unprivileged it becomes
# the build user's, which is what proot presents on the device anyway.
tar -xzf base.tar.gz -C rootfs --no-same-owner --no-same-permissions --exclude=dev 2>/dev/null || true
while read -r entry; do
  file=${entry#*/}
  tar -xf "pkgs/$file" -C rootfs --no-same-owner --no-same-permissions \
    --exclude=.PKGINFO --exclude=.MTREE --exclude=.INSTALL --exclude=.BUILDINFO --exclude=.CHANGELOG
done < pkglist.txt

# Everything is read and written as one unprivileged user, here and on the device: a package that
# ships a setuid helper without owner read (dbus-daemon-launch-helper) would otherwise fail the
# final tar after the whole build.
chmod -R u+rwX rootfs
# Arch's Turnip only knows the msm DRM kernel driver; Android reaches the Adreno through KGSL.
# build-turnip.sh cross-builds Mesa's Turnip with the KGSL backend against this rootfs.
"$here/build-turnip.sh" "$work/turnip" "$work/rootfs"
install -m 755 "$work/turnip/libvulkan_freedreno.so" rootfs/usr/lib/libvulkan_freedreno.so
# A driver fix reaches an installed runtime by importing a "-Linux" Turnip zip (Contents ->
# Installed -> Linux runtime drivers), which the session then loads through VK_DRIVER_FILES without
# touching the rootfs - see core/LinuxVulkanDriver. So the app does NOT carry a copy of this driver:
# 15MB in every apk to do what a 3MB zip already does, and it would freeze the choice besides. The
# driver in this rootfs is the default the runtime ships with.
printf '{\n    "ICD": {\n        "api_version": "1.4.0",\n        "library_path": "/usr/lib/libvulkan_freedreno.so"\n    },\n    "file_format_version": "1.0.0"\n}\n' \
  > rootfs/usr/share/vulkan/icd.d/freedreno_icd.json
rm -f rootfs/usr/share/vulkan/icd.d/nvidia_icd.json

# Steam's arm64 UI (steamui.so, vgui2_s.so) still links GTK 2, which Arch no longer packages;
# Debian's build links only sonames the rootfs has, so its two libraries are enough.
gtk2_deb=libgtk2.0-0t64_2.24.33-7_arm64.deb
gtk2_sha=28b2f1622197443f07f25a93e03db1a964184946ac12f501b8221c895026d0ca
[ -s "pkgs/$gtk2_deb" ] || curl -fsSL --retry 6 --retry-delay 5 --retry-all-errors -o "pkgs/$gtk2_deb" "http://deb.debian.org/debian/pool/main/g/gtk+2.0/$gtk2_deb"
echo "$gtk2_sha  pkgs/$gtk2_deb" | sha256sum -c --quiet
rm -rf gtk2 && mkdir gtk2 && (cd gtk2 && ar x "../pkgs/$gtk2_deb" && tar -xf data.tar.*)
for n in gtk gdk; do
  install -m 755 "gtk2/usr/lib/aarch64-linux-gnu/lib$n-x11-2.0.so.0.2400.33" rootfs/usr/lib/
  ln -sfn "lib$n-x11-2.0.so.0.2400.33" "rootfs/usr/lib/lib$n-x11-2.0.so.0"
done

# Proton ships its own GStreamer plugins but not the libraries they link against, and Arch's are all
# too new to satisfy them: nettle 4.0 gives libnettle.so.9 (wanted .8), libtheora 1.2.0 gives
# libtheoradec.so.2 (wanted .1), libvpx 1.17 gives libvpx.so.12 (wanted .9). Without these the HLS,
# theora and vpx plugins fail to load and anything that plays video through Proton's media stack -
# the EA app's onboarding, in-game intro movies - silently has no decoder. Same trick as the GTK 2
# fetch above: take the exact sonames from a distro that still ships them.
gst_debs="
libnettle8_3.8.1-2_arm64.deb c945ff210df69cf7b95e935b8fa936e81c1c1f475355e3d5db83510b174f0cd6 https://deb.debian.org/debian/pool/main/n/nettle/libnettle8_3.8.1-2_arm64.deb
libtheora0_1.1.1+dfsg.1-16.1build3_arm64.deb 78ebaa1c851465dac9e13532623a4e41d28ed55f3df0353daf7c29d96a2e2b87 http://ports.ubuntu.com/ubuntu-ports/pool/main/libt/libtheora/libtheora0_1.1.1+dfsg.1-16.1build3_arm64.deb
libvpx9_1.14.0-1ubuntu2_arm64.deb 809bf0d9435520793838a99072ca365ab23956def3583c3b8b4e253135d8e9f4 http://ports.ubuntu.com/ubuntu-ports/pool/main/libv/libvpx/libvpx9_1.14.0-1ubuntu2_arm64.deb
"
rm -rf gstlibs && mkdir gstlibs
# A here-string, not a pipe: a pipeline's loop body runs in a subshell, where a failed
# sha256sum -c would not abort the build and the guard would be decorative.
while read -r deb sha url; do
  [ -n "$deb" ] || continue
  [ -s "pkgs/$deb" ] || curl -fsSL --retry 6 --retry-delay 5 --retry-all-errors -o "pkgs/$deb" "$url"
  echo "$sha  pkgs/$deb" | sha256sum -c --quiet
  (cd gstlibs && ar x "../pkgs/$deb" && tar -xf data.tar.* && rm -f data.tar.* control.tar.* debian-binary)
done <<< "$gst_debs"
# Copy the real objects and re-create the soname symlinks the loader actually resolves.
find gstlibs/usr/lib -maxdepth 2 -name '*.so.*' -type f -exec cp -a {} rootfs/usr/lib/ \;
for so in rootfs/usr/lib/libnettle.so.8.* rootfs/usr/lib/libtheoradec.so.1.* rootfs/usr/lib/libtheoraenc.so.1.* \
          rootfs/usr/lib/libtheora.so.0.* rootfs/usr/lib/libvpx.so.9.*; do
  [ -f "$so" ] || continue
  base=$(basename "$so")
  ln -sfn "$base" "rootfs/usr/lib/$(echo "$base" | sed -E 's/(\.so\.[0-9]+).*/\1/')"
done
ls -l rootfs/usr/lib/libnettle.so.8 rootfs/usr/lib/libtheoradec.so.1 rootfs/usr/lib/libvpx.so.9

cp -a "$here/overlay/." rootfs/
# The app carries the same scripts and refreshes them into an installed rootfs at every session
# start, so a build and the rootfs it boots never disagree about them (the rootfs cannot be
# written from outside the app, and the device may never take a new runtime image).
for script in "$here"/overlay/usr/local/bin/bannerlator-*; do
  install -Dm644 "$script" "$here/../../app/src/main/assets/linuxfs/usr/local/bin/$(basename "$script")"
done
# Preloaded into every session process: what the kernel or the app sandbox withholds, answered
# in the process itself; see preload/*.c.
mkdir -p rootfs/usr/local/lib
aarch64-linux-gnu-gcc -shared -fPIC -O2 -Wall -pthread -o rootfs/usr/local/lib/libblsession.so "$here"/preload/*.c -ldl
# The Steam client's own copy of the fake-evdev interposer, built against glibc for the session.
# Deliberately a SEPARATE source from the one preloaded into Wine: what this needs - a virtual
# gamepad identity, the triggers where an X-Box pad keeps them, a Steam button - is what the
# client wants and not what a Windows game does, and sharing one file meant every change made for
# the client landed in every Wine game too. The app's on-screen and physical pads are published as shared state files (see
# HermesWriter); this serves them to a client as a real /dev/input/eventN, which is how the
# native Steam client gets a controller at all - it has no Wine and no XInput to read. Preloaded
# only on the client, by bannerlator-session, not from ld.so.preload: every interposed call here
# sits on open/read/poll/select, and nothing else in the session needs a gamepad.
# The transport's guest side (hermes) is a Rust static library linked into it, built and link-probed here
# the same way the app's CI builds it, and kept out of the exported symbols.
hermes="$here/../../app/src/main/cpp/hermes"
"$hermes/ci/build.sh" glibc
aarch64-linux-gnu-g++ -shared -fPIC -O2 -Wall -Wno-attributes -Wno-nonnull-compare -pthread -std=c++17 -static-libstdc++ -static-libgcc \
  -Wl,--no-undefined -Wl,--exclude-libs,libhermes_guest.a \
  -o rootfs/usr/local/lib/libfakeinput.so "$here/../../app/src/main/cpp/winlator/fakeinput_steam.cpp" \
  "$hermes/target/aarch64-unknown-linux-gnu/release/libhermes_guest.a" -ldl
aarch64-linux-gnu-readelf -d rootfs/usr/local/lib/libfakeinput.so | grep NEEDED
# Games that ship a native Linux x86 build run under FEX, and the aarch64 library above cannot be
# loaded into an x86 process - so those get their own copies. Only the System V IPC shim is built
# for them: Android kernels have no System V IPC, shmget/semget/msgget return ENOSYS, and Source's
# tier0 gives up on that ("create pipe failed ... Function not implemented"). The other shims
# answer host-side concerns that do not arise inside the emulated process.
# Cross compilers rather than gcc-multilib: multilib conflicts with the aarch64 cross gcc this
# build already needs, and apt refuses to install both.
for guest in i686 x86_64; do
  # Not $out: that already holds the path of the tarball this script writes at the end.
  shim=rootfs/usr/local/lib/libblsysv-$guest.so
  # The package is gcc-x86-64-linux-gnu but the binary it ships keeps the underscore.
  "$guest-linux-gnu-gcc" -shared -fPIC -O2 -Wall -pthread -o "$shim" "$here"/preload/sysv.c -ldl
  echo "  built $shim"
done
# Also beside the tarball, so a device test can take a few kilobytes instead of the whole rootfs.
# This script has cd'd into the work dir, so only it knows where both of those actually are.
mkdir -p "$(dirname "$out")/guest-shims"
cp rootfs/usr/local/lib/libblsysv-*.so "$(dirname "$out")/guest-shims/"
# proot and its loader are Android/bionic binaries: they are not part of this rootfs, they are what
# creates it. They ride along in the tarball so that installing the runtime delivers them, which
# keeps an app reinstall from replacing the one binary the whole runtime depends on. See
# prebuilt/proot/README.md for why they are prebuilt and for the licence and source pointers.
mkdir -p rootfs/opt/android-host
cp -a "$here"/prebuilt/proot/proot "$here"/prebuilt/proot/loader \
      "$here"/prebuilt/proot/libtalloc.so.2 "$here"/prebuilt/proot/README.md rootfs/opt/android-host/
chmod 755 rootfs/opt/android-host/proot rootfs/opt/android-host/loader
mkdir -p rootfs/dev rootfs/proc rootfs/sys rootfs/tmp rootfs/root rootfs/run/user
chmod 1777 rootfs/tmp
# The dynamic loader takes its search path from here; ldconfig cannot run without the target CPU.
printf '/usr/local/lib\n/usr/lib\n/usr/lib32\n' > rootfs/etc/ld.so.conf
rm -f rootfs/etc/ld.so.cache
# Xwayland and Steam want a machine id and a resolver.
rm -f rootfs/etc/machine-id rootfs/etc/resolv.conf
head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n' > rootfs/etc/machine-id
printf 'nameserver 8.8.8.8\nnameserver 1.1.1.1\n' > rootfs/etc/resolv.conf
printf 'root:x:0:0:root:/root:/bin/bash\n' > rootfs/etc/passwd
printf 'root:x:0:\n' > rootfs/etc/group

# What pacman's hooks would have built: the mime database, pixbuf loader and icon caches,
# GSettings schemas and the font cache. Only these run from the rootfs, under qemu.
proot -q "$(command -v qemu-aarch64-static)" -r rootfs -w / -b /dev -b /proc /bin/bash -c '
  export PATH=/usr/bin:/bin
  mkdir -p /usr/lib/gdk-pixbuf-2.0/2.10.0
  update-mime-database /usr/share/mime
  gdk-pixbuf-query-loaders --update-cache
  glib-compile-schemas /usr/share/glib-2.0/schemas
  fc-cache -f
  for d in /usr/share/icons/*/; do [ -f "$d/index.theme" ] && gtk-update-icon-cache -q -t -f "$d"; done
  rm -rf /root/.cache' >/dev/null

tar -C rootfs -cf - . | zstd -T0 -19 -o "$out" --force
ls -la "$out"

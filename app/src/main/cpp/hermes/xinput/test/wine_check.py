#!/usr/bin/env python3
"""wine_check.py <our dlls dir> <search root>...  (needs llvm-readobj on PATH; build.sh wine-check)

Interface check of HERMES-XInput against the Wine installed on the CI runner. Only the export and
import TABLES of Wine's built modules are read (names and ordinals), nothing else.

For every Wine "<arch>-windows" directory found under the search roots (x86_64-windows -> our
x86_64 build, i386-windows -> i386, aarch64-windows -> arm64x; an ARM64X Wine module is compared view
by view: native view with our native view, EC view with our EC view):

1. Exports, per XInput DLL and view: every NAME Wine's builtin exports must be exported by ours, and
   every ORDINAL Wine exports must be exported by ours at the same number, under the same name when
   Wine gives it one (ours may export more: the named extended functions 100-103 everywhere).
2. Imports: every Wine module in those directories that imports from an XInput DLL (joy.cpl, ...)
   must find each imported name / ordinal in our DLL of the same name, arch and view.
Exit status 1 if anything is missing.
"""
import os
import re
import subprocess
import sys

DLLS = ["xinput1_1", "xinput1_2", "xinput1_3", "xinput1_4", "xinput9_1_0", "xinputuap"]
ARCH = {"x86_64-windows": "x86_64", "i386-windows": "i386", "aarch64-windows": "arm64x"}
PE_SUFFIXES = (".dll", ".exe", ".cpl", ".drv", ".sys", ".ocx", ".acm", ".ax", ".tlb", ".ds", ".msstyles")


def readobj(flag, path):
    return subprocess.run(["llvm-readobj", flag, path], capture_output=True, text=True, check=True).stdout


def views(text):
    """{'main': lines, 'hybrid': lines} ('hybrid' only for an ARM64X image)."""
    out = {"main": []}
    cur = out["main"]
    for line in text.splitlines():
        if "HybridObject {" in line:
            out["hybrid"] = cur = []
            continue
        cur.append(line)
    return out


def exports(path):
    """{view: {ordinal: name or None}} (unused ordinal slots, RVA 0, left out)."""
    res = {}
    for v, lines in views(readobj("--coff-exports", path)).items():
        ents, o, n, r = {}, None, None, None
        for line in lines:
            s = line.strip()
            if s.startswith("Export {"):
                o, n, r = None, None, None
            elif s.startswith("Ordinal:"):
                o = int(s.split()[1])
            elif s.startswith("Name:"):
                parts = s.split(None, 1)
                n = parts[1] if len(parts) > 1 else None
            elif s.startswith("RVA:"):
                r = s.split()[1]
            elif s == "}" and o is not None:
                if r not in (None, "0x0"):
                    ents[o] = n
                o = None
        res[v] = ents
    return res


def imports(path):
    """{view: [(dll, name or None, ordinal or None)]} incl. delay imports."""
    res = {}
    for v, lines in views(readobj("--coff-imports", path)).items():
        out, dll = [], None
        for line in lines:
            s = line.strip()
            if s.startswith("Import {") or s.startswith("DelayImport {"):
                dll = None
            elif s.startswith("Name:") and dll is None:
                dll = s.split(None, 1)[1].lower() if len(s.split(None, 1)) > 1 else ""
            elif s.startswith("Symbol:"):
                m = re.match(r"Symbol:\s*(.*?)\s*\((\d+)\)\s*$", s)
                if m:
                    name = m.group(1) or None
                    out.append((dll, name, None if name else int(m.group(2))))
        res[v] = out
    return res


def main():
    ours_dir, roots = sys.argv[1], sys.argv[2:]
    wdirs = []
    for root in roots:
        for d, subdirs, _ in os.walk(root):
            if os.path.basename(d) in ARCH and "wine" in d.lower():
                wdirs.append(d)
                subdirs[:] = []
    wdirs = sorted(set(wdirs))
    if not wdirs:
        sys.exit("no Wine *-windows directories under %s" % roots)
    fails, notes, cache = [], [], {}

    def ours(arch, dll):
        key = (arch, dll)
        if key not in cache:
            p = os.path.join(ours_dir, arch, dll + ".dll")
            cache[key] = exports(p) if os.path.isfile(p) else None
        return cache[key]

    for wdir in wdirs:
        arch = ARCH[os.path.basename(wdir)]
        print("== %s  (compared with our %s build)" % (wdir, arch))
        for dll in DLLS:
            wpath = os.path.join(wdir, dll + ".dll")
            if not os.path.isfile(wpath):
                print("   %s.dll: not in this Wine" % dll)
                continue
            mine = ours(arch, dll)
            if mine is None:
                fails.append("%s/%s.dll: we ship no such DLL" % (arch, dll))
                continue
            for v, wine in exports(wpath).items():
                ov = v if arch == "arm64x" else "main"
                our = mine.get(ov, {})
                our_names = {n for n in our.values() if n}
                tag = "%s/%s.dll%s" % (arch, dll, " [%s view]" % v if arch == "arm64x" else "")
                missing = []
                for o, n in sorted(wine.items()):
                    if n and n not in our_names:
                        missing.append("name %s" % n)
                    if o not in our:
                        missing.append("ordinal %d%s" % (o, " (%s)" % n if n else ""))
                    elif n and our[o] != n:
                        missing.append("ordinal %d is %s in ours, %s in Wine" % (o, our[o] or "unnamed", n))
                print("   %-34s Wine %2d exports, ours %2d: %s" % (tag, len(wine), len(our), "MISSING " + ", ".join(missing) if missing else "superset ok"))
                print("      Wine: %s" % ", ".join("%d %s" % (o, n or "-") for o, n in sorted(wine.items())))
                for m in missing:
                    fails.append("%s: %s" % (tag, m))
        users = 0
        for f in sorted(os.listdir(wdir)):
            p = os.path.join(wdir, f)
            lf = f.lower()
            if not lf.endswith(PE_SUFFIXES) or lf.startswith("xinput") or not os.path.isfile(p):
                continue
            with open(p, "rb") as fh:
                if b"xinput" not in fh.read().lower():
                    continue
            try:
                imp = imports(p)
            except subprocess.CalledProcessError:
                continue
            for v, entries in imp.items():
                ov = v if arch == "arm64x" else "main"
                for d, n, o in entries:
                    base = d[:-4] if d and d.endswith(".dll") else d
                    if base not in DLLS:
                        continue
                    users += 1
                    mine = ours(arch, base)
                    our = (mine or {}).get(ov, {})
                    ok = (n in set(our.values())) if n else (o in our)
                    what = "%s!%s" % (d, n if n else "#%d" % o)
                    print("   %s%s imports %s: %s" % (f, " [%s view]" % v if arch == "arm64x" else "", what, "resolves in ours" if ok else "MISSING in ours"))
                    if not ok:
                        fails.append("%s/%s imports %s, which ours lacks" % (arch, f, what))
        if not users:
            print("   no other module here imports from an XInput DLL")
    for n in notes:
        print("note: " + n)
    if fails:
        print("\n%d problem(s):" % len(fails))
        for f in fails:
            print("  " + f)
        sys.exit(1)
    print("\nwine-check: every Wine export and every Wine import of an XInput DLL is covered by ours")


if __name__ == "__main__":
    main()

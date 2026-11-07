#!/usr/bin/env python3
"""Assemble a Bannerlator release description in the house layout.

    python3 scripts/release_notes.py <release_number> [--prerelease] [--fallback TEXT]

Writes release-body.md (the GitHub release description) and short-notes.txt (the one line
update.json carries for the in-app updater). Used by release.yml, and by
release-notes-check.yml for a dry run that publishes nothing.

Stable releases: docs/releases/<release_number>.md is REQUIRED. It must carry the standard
layout (logo + version badge, title, "What's New — everything since <previous stable>" naming
the real previous stable tag, Carried over, and Where to get Proton 9 + Credits as collapsed
tap-to-expand <details> blocks) and no unfinished TODO(notes) placeholders. The complete list
of merged work since the previous stable tag is appended in a collapsible section, so nothing
merged is ever left off the page.

Pre-releases: the notes file is used when present; otherwise the --fallback text is the body,
as before.

The update.json line comes from an optional <!-- update-summary: ... --> comment in the notes
file, else --fallback, else the notes' bold lead paragraph (plain text, trimmed).
"""
import argparse
import os
import re
import subprocess
import sys

REPO_URL = "%s/%s" % (os.environ.get("GITHUB_SERVER_URL", "https://github.com"),
                      os.environ.get("GITHUB_REPOSITORY", "winhub-emu/winhub"))
# Every release page opens with this logo, centered. A notes file may carry its own older logo header: it
# is swapped for this one, so a page never shows two.
LOGO_URL = "https://raw.githubusercontent.com/winhub-emu/winhub/refs/heads/master/logo.png"
LOGO_BLOCK = ('<p align="center">\n<img src="%s" width="1500" height="500" alt="WinHub" />\n</p>\n\n'
              % LOGO_URL)
OLD_LOGO = re.compile(r'<p align="center">\s*<img src="[^"]*logo\.(?:jpg|png)[^"]*"[^>]*>\s*</p>\s*',
                      re.I)
STABLE_TAG = re.compile(r"^\d+\.\d+(\.\d+)?$")
PLACEHOLDER = "TODO(notes)"
# The two standing sections every stable page carries, collapsed behind a tap-to-expand summary.
PROTON_SUMMARY = "<summary><b>📦 Where to get Proton 9</b> (tap to expand)</summary>"
CREDITS_SUMMARY = "<summary><b>🙏 Credits</b> (tap to expand)</summary>"

# First-parent commits that are bookkeeping, not work a user would see.
SKIP_SUBJECT = re.compile(
    r"^(PROGRESS_LOG|release:|ci[(:]|chore[(:]|build[(:]|test[(:]|style[(:]|refactor[(:]"
    r"|Merge branch 'main'|Merge remote-tracking)", re.I)
# Merged branches that are release / CI housekeeping.
SKIP_BRANCH = ("release/", "ci/", "chore/")
MERGE_BRANCH = re.compile(r"^Merge branch '([^']+)'(?: into \S+)?(?::\s*(.*))?$")
MERGE_PLAIN = re.compile(r"^Merge ([a-z]+/[^\s:]+)(?::\s*(.*))?$")
MERGE_PR = re.compile(r"^Merge pull request #(\d+) from \S+?/(\S+)")
CONVENTIONAL = re.compile(r"^(?:feat|fix|docs|perf)(?:\([^)]*\))?!?:\s*(.*)$", re.I)


def fail(msg):
    print("::error::" + msg, file=sys.stderr)
    sys.exit(1)


def git(*args):
    return subprocess.run(["git"] + list(args), check=True, capture_output=True, text=True).stdout


def previous_stable(ver):
    for tag in git("tag", "--merged", "HEAD", "--sort=-v:refname").split():
        if STABLE_TAG.match(tag) and tag != ver:
            return tag
    fail("no previous stable tag is reachable from HEAD")


def strip_conventional(subject):
    m = CONVENTIONAL.match(subject)
    return m.group(1) if m else subject


def change_list(prev):
    """One line per merge (or direct feature commit) on main's first-parent line since prev."""
    items = []
    log = git("log", "--first-parent", "--reverse", "--format=%H%x09%P%x09%s", prev + "..HEAD")
    for line in log.splitlines():
        sha, parents, subject = line.split("\t", 2)
        parents = parents.split()
        if SKIP_SUBJECT.match(subject):
            continue
        branch = desc = None
        m = MERGE_BRANCH.match(subject) or MERGE_PLAIN.match(subject)
        if m:
            branch, desc = m.group(1), m.group(2)
        else:
            m = MERGE_PR.match(subject)
            if m:
                branch, desc = m.group(2), "pull request #" + m.group(1)
            elif len(parents) > 1:
                branch = "merge"
        if branch and branch.startswith(SKIP_BRANCH):
            continue
        if branch and not desc and len(parents) > 1:
            # A merge with no description: name it after the branch's first real commit.
            subs = [s for s in git("log", "--no-merges", "--format=%s",
                                   parents[0] + ".." + parents[1]).splitlines()
                    if not SKIP_SUBJECT.match(s)]
            desc = strip_conventional(subs[-1]) if subs else branch
        if not branch:
            desc = strip_conventional(subject)
        desc = desc.strip()
        desc = desc[:1].upper() + desc[1:]
        link = "[`%s`](%s/commit/%s)" % (sha[:8], REPO_URL, sha)
        items.append("- %s — `%s` · %s" % (desc, branch, link) if branch and branch != "merge"
                     else "- %s · %s" % (desc, link))
    return items


def changes_section(prev, items):
    return ("<details>\n"
            "<summary><b>📜 Every change since %s</b> — %d merged, oldest first</summary>\n\n"
            "%s\n\n</details>\n" % (prev, len(items), "\n".join(items)))


def insert_before_credits(body, section):
    """Put the change list just above the Credits block (and the '---' that opens it)."""
    lines = body.split("\n")
    try:
        credits = next(i for i, l in enumerate(lines)
                       if "🙏 Credits" in l and (l.startswith("## ") or l.startswith("<summary>")))
    except StopIteration:
        return body.rstrip("\n") + "\n\n" + section

    def prev_nonblank(i):
        i -= 1
        while i >= 0 and not lines[i].strip():
            i -= 1
        return i

    at = credits
    j = prev_nonblank(at)
    if lines[credits].startswith("<summary>") and j >= 0 and lines[j].strip() == "<details>":
        at = j
        j = prev_nonblank(at)
    if j >= 0 and lines[j].strip() == "---":
        at = j
    return "\n".join(lines[:at] + [section.rstrip("\n"), ""] + lines[at:])


def plain(markdown):
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", markdown)
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"[*_`]", "", text)
    return re.sub(r"\s+", " ", text).strip()


def short_notes(body, fallback, title):
    m = re.search(r"<!--\s*update-summary:\s*(.*?)\s*-->", body, re.S)
    if m:
        return plain(m.group(1))
    if fallback.strip():
        return plain(fallback)
    # The bold lead paragraph right after the title/tagline.
    for para in re.split(r"\n\s*\n", body):
        p = para.strip()
        if p.startswith("**"):
            text = plain(p)
            if len(text) > 500:
                cut = text[:500]
                text = cut[:cut.rfind(". ") + 1] if ". " in cut else cut.rstrip() + "…"
            return text
    return title


def with_logo(body):
    """Body with the WinHub logo centered at the very top."""
    if LOGO_URL in body:
        return body
    if OLD_LOGO.search(body):
        return OLD_LOGO.sub(lambda m: LOGO_BLOCK, body, count=1)
    return LOGO_BLOCK + body


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("release_number")
    ap.add_argument("--prerelease", action="store_true")
    ap.add_argument("--fallback", default="")
    ap.add_argument("--title", default="")
    ap.add_argument("--out-body", default="release-body.md")
    ap.add_argument("--out-short", default="short-notes.txt")
    a = ap.parse_args()

    ver = a.release_number
    notes_path = "docs/releases/%s.md" % ver
    title = a.title or "Bannerlator " + ver

    if not os.path.isfile(notes_path):
        if not a.prerelease:
            fail("%s is missing. A stable release needs its full notes there, in the same layout "
                 "as the previous release (start one with scripts/new_release_notes.py)." % notes_path)
        body = a.fallback or title
        print("pre-release without %s: using the fallback text as the body" % notes_path)
    else:
        body = open(notes_path, encoding="utf-8").read()
        if not a.prerelease:
            prev = previous_stable(ver)
            required = [
                ("the logo header", "blob/main/logo.jpg"),
                ("the %s version badge" % ver, "img.shields.io/badge/release-%s-" % ver),
                ("the title", "# Bannerlator %s" % ver),
                ("What's New since the previous stable (%s)" % prev,
                 "# What's New — everything since %s" % prev),
                ("the Carried over section", "## ⚠️ Carried over — still open"),
                ("the collapsed Proton 9 section", PROTON_SUMMARY.split("</b>")[0] + "</b>"),
                ("the collapsed Credits section", CREDITS_SUMMARY.split("</b>")[0] + "</b>"),
            ]
            missing = [name for name, needle in required if needle not in body]
            if missing:
                fail("%s does not follow the release layout; missing: %s"
                     % (notes_path, "; ".join(missing)))
            if PLACEHOLDER in body:
                fail("%s still has %s placeholders" % (notes_path, PLACEHOLDER))
            items = change_list(prev)
            body = insert_before_credits(body, changes_section(prev, items))
            print("stable %s: layout OK, %d changes since %s appended" % (ver, len(items), prev))

    short = short_notes(body, a.fallback, title)
    body = with_logo(body)
    with open(a.out_body, "w", encoding="utf-8") as f:
        f.write(body if body.endswith("\n") else body + "\n")
    with open(a.out_short, "w", encoding="utf-8") as f:
        f.write(short + "\n")
    print("update.json notes: " + short)


if __name__ == "__main__":
    main()

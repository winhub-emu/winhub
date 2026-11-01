#!/usr/bin/env python3
"""Reproduce the pinned Nexus source and DroidDeck credential transport."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

recipe = Path(__file__).resolve().parent
cache = Path(sys.argv[1]).resolve()
lock = json.loads((recipe / 'backend.lock.json').read_text())
local_patches = ['droiddeck-credentials.patch', 'droiddeck-launch-view.patch', 'droiddeck-library-subscriptions.patch',
                 'bannerlator-android.patch', 'bannerlator-diagnostics.patch',
                 'bannerlator-parallel-download.patch', 'bannerlator-cloud-token.patch']
identity = hashlib.sha256((recipe / 'backend.lock.json').read_bytes() + b''.join((recipe / name).read_bytes() for name in local_patches)).hexdigest()
source = cache / ('source-' + identity[:16])
if not (source / '.prepared').is_file():
    if source.exists():
        raise RuntimeError('Incomplete Xbox source directory; inspect it before retrying: ' + str(source))
    source.mkdir(parents=True)
    nexus = cache / ('nexus-' + lock['nexusCommit'])
    for directory, remote, commit in ((source, lock['repository'], lock['commit']),
                                     (nexus, 'https://github.com/domsvoboda/xodus-nexus.git', lock['nexusCommit'])):
        directory.mkdir(parents=True, exist_ok=True)
        if not (directory / '.git').is_dir():
            subprocess.run(['git', 'init', '-q', str(directory)], check=True)
            subprocess.run(['git', '-C', str(directory), 'fetch', '-q', '--depth', '1', remote, commit], check=True)
            subprocess.run(['git', '-C', str(directory), 'checkout', '-q', '--detach', 'FETCH_HEAD'], check=True)
        assert subprocess.check_output(['git', '-C', str(directory), 'rev-parse', 'HEAD'], text=True).strip() == commit
    assert hashlib.sha256((source / 'Cargo.lock').read_bytes()).hexdigest() == lock['cargoLockSha256']
    for patch in lock['patches']:
        file = nexus / 'assets' / patch['file']
        assert hashlib.sha256(file.read_bytes()).hexdigest() == patch['sha256']
        subprocess.run(['git', '-C', str(source), 'apply', str(file)], check=True)
    for name in local_patches:
        subprocess.run(['git', '-C', str(source), 'apply', str(recipe / name)], check=True)
    (source / '.prepared').write_text(identity + '\n')
assert (source / '.prepared').read_text().strip() == identity
print(source)

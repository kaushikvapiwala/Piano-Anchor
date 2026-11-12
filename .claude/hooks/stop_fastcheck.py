#!/usr/bin/env python3
"""Stop hook: Claude may not finish while `./gradlew fastCheck` fails.

Skips the run when the working tree is unchanged since the last result (fingerprint of HEAD, the diff and
untracked files, kept in .git/). A tree that already failed and was reported is not re-checked or blocked
again (in this or a later turn), so a failure Claude can't fix (e.g. a broken toolchain) can't loop forever.
Outside a usable git repo the hook allows the stop with a warning.
"""
from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

from hooklib import gradle, project_root


def git(root: Path, *args: str) -> bytes:
    return subprocess.run(["git", *args], cwd=root, capture_output=True, check=True).stdout


def fingerprint(root: Path) -> str:
    digest = hashlib.sha256()
    digest.update(git(root, "rev-parse", "HEAD"))
    digest.update(git(root, "diff", "HEAD", "--binary"))
    for name in git(root, "ls-files", "--others", "--exclude-standard", "-z").split(b"\0"):
        file = root / os.fsdecode(name)
        if name and file.is_file():
            digest.update(name + b"\0" + file.read_bytes())
    return digest.hexdigest()


def warn(message: str) -> int:
    print(json.dumps({"systemMessage": message}))
    return 0


def main() -> int:
    event = json.load(sys.stdin)
    root = project_root(event)
    try:
        state = root / git(root, "rev-parse", "--git-path", "claude-fastcheck").decode().strip()
        current = fingerprint(root)
    except (subprocess.CalledProcessError, OSError) as error:
        return warn(f"Stop hook skipped fastCheck: no usable git repository ({error}).")

    # The state file holds "<pass|fail> <fingerprint>" of the last run.
    last = state.read_text() if state.exists() else ""
    if last == f"pass {current}":
        return 0
    if last == f"fail {current}":
        return warn("fastCheck still fails and nothing changed since it was reported; not blocking again.")

    failure = gradle(root, "fastCheck")
    state.write_text(f"{'fail' if failure else 'pass'} {current}")
    if failure:
        print(f"./gradlew fastCheck fails. Fix it before finishing:\n{failure}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())

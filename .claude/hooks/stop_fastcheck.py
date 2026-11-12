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

from gradle_output import failure_summary


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
    root = Path(os.environ.get("CLAUDE_PROJECT_DIR") or event.get("cwd") or ".").resolve()
    try:
        state_dir = root / git(root, "rev-parse", "--git-path", "claude-hooks").decode().strip()
        current = fingerprint(root)
    except (subprocess.CalledProcessError, OSError) as error:
        return warn(f"Stop hook skipped fastCheck: no usable git repository ({error}).")
    state_dir.mkdir(parents=True, exist_ok=True)
    passed, failed = state_dir / "fastcheck-passed", state_dir / "fastcheck-failed"

    if passed.exists() and passed.read_text() == current:
        return 0
    if failed.exists() and failed.read_text() == current:
        return warn("fastCheck still fails and nothing changed since it was reported; not blocking again.")

    result = subprocess.run(["./gradlew", "--quiet", "fastCheck"], cwd=root, capture_output=True, text=True)
    if result.returncode == 0:
        passed.write_text(current)
        return 0
    failed.write_text(current)
    output = failure_summary(result.stdout + result.stderr)
    print(f"./gradlew fastCheck fails. Fix it before finishing:\n{output}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())

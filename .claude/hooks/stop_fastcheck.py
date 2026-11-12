#!/usr/bin/env python3
"""Stop hook: Claude may not finish while `./gradlew fastCheck` fails.

Skips the run when the working tree is unchanged since the last pass (fingerprint of HEAD, the diff and
untracked files, kept in .git/). If Claude stops again without changing anything after being blocked, the
stop is allowed with a warning, so a failure Claude can't fix (e.g. a broken toolchain) can't loop forever.
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
        file = root / name.decode()
        if name and file.is_file():
            digest.update(name + b"\0" + file.read_bytes())
    return digest.hexdigest()


def main() -> int:
    event = json.load(sys.stdin)
    root = Path(os.environ.get("CLAUDE_PROJECT_DIR") or event.get("cwd") or ".").resolve()
    state_dir = root / git(root, "rev-parse", "--git-path", "claude-hooks").decode().strip()
    state_dir.mkdir(parents=True, exist_ok=True)
    passed, failed = state_dir / "fastcheck-passed", state_dir / "fastcheck-failed"
    current = fingerprint(root)

    if passed.exists() and passed.read_text() == current:
        return 0
    if event.get("stop_hook_active") and failed.exists() and failed.read_text() == current:
        warning = "fastCheck still fails and nothing changed since; stopping anyway. Tell the user it fails."
        print(json.dumps({"systemMessage": warning}))
        return 0

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

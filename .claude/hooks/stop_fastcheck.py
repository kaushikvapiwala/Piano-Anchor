#!/usr/bin/env python3
"""Stop hook: Claude may not finish while `./gradlew fastCheck` fails.

Also the SubagentStop hook for the `implementer` subagent. Skips the run when the working tree is unchanged
since the last result (its git tree id, kept in .git/), so committing a checked tree doesn't re-run it.
A tree that already failed and was reported to the same caller (main session or one subagent) is not
re-checked or blocked again, so a failure Claude can't fix (e.g. a broken toolchain) can't loop forever;
a different caller is still blocked.
Outside a usable git repo the hook allows the stop with a warning.
"""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from hooklib import gradle, project_root


def git(root: Path, *args: str, env: dict[str, str] | None = None) -> bytes:
    return subprocess.run(["git", *args], cwd=root, env=env, capture_output=True, check=True).stdout


def fingerprint(root: Path) -> str:
    """Tree id of the working tree (tracked and untracked, not ignored), via a throwaway index."""
    with tempfile.TemporaryDirectory() as tmp:
        index = Path(tmp) / "index"
        env = {**os.environ, "GIT_INDEX_FILE": str(index)}
        # Start from a copy of the real index (stat data kept) so only changed files are re-hashed.
        real = root / git(root, "rev-parse", "--git-path", "index").decode().strip()
        if real.is_file():
            shutil.copy2(real, index)
        else:
            git(root, "read-tree", "HEAD", env=env)
        git(root, "add", "--all", env=env)
        return git(root, "write-tree", env=env).decode().strip()


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

    # The state file holds "pass <fingerprint>" or "fail <fingerprint> <caller>" of the last run.
    caller = event.get("agent_id") or "main"
    last = state.read_text() if state.exists() else ""
    if last == f"pass {current}":
        return 0
    if last == f"fail {current} {caller}":
        return warn("fastCheck still fails and nothing changed since it was reported; not blocking again.")

    failure = gradle(root, "fastCheck")
    state.write_text(f"fail {current} {caller}" if failure else f"pass {current}")
    if failure:
        print(f"./gradlew fastCheck fails. Fix it before finishing:\n{failure}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())

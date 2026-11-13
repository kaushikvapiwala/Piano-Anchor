#!/usr/bin/env python3
"""Stop hook: Claude may not finish while `./gradlew fastCheck` fails.

Also the SubagentStop hook for the `implementer` subagent. Skips the run when the working tree is unchanged
since the last result (its git tree id, kept in .git/), so committing a checked tree doesn't re-run it.
A tree that already failed and was reported to the same caller (main session or one subagent) is not
re-checked or blocked again, so a failure Claude can't fix (e.g. a broken toolchain) can't loop forever;
a different caller is still blocked.
Outside a usable git repo the hook allows the stop with a warning.
`PIANO_ANCHOR_STOP_HOOK=subagent-only` turns the main-session Stop check off (the cloud dispatcher sets it, so it
doesn't build under its subagents); SubagentStop still checks. A running Gradle build in the repo skips the check.
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


def own_ancestors() -> set[int]:
    """This process and its ancestors (the hook may run under a Gradle-spawned shell)."""
    pids, pid = set(), os.getpid()
    while pid > 1 and pid not in pids:
        pids.add(pid)
        out = subprocess.run(["ps", "-o", "ppid=", "-p", str(pid)], capture_output=True, text=True).stdout.strip()
        pid = int(out) if out.isdigit() else 0
    return pids


def other_build_running(root: Path) -> bool:
    """True if a Gradle wrapper client (it exists only while a build runs; idle daemons don't count) is running.

    On Linux only clients whose /proc/<pid>/cwd is inside `root` count. macOS has no cheap equivalent, so there
    any GradleWrapperMain process counts (a build in another project also skips the check; the next stop re-checks).
    """
    ps = subprocess.run(["ps", "-Ao", "pid=,args="], capture_output=True, text=True).stdout
    mine = own_ancestors()
    for line in ps.splitlines():
        pid, _, args = line.strip().partition(" ")
        if not pid.isdigit() or int(pid) in mine or "GradleWrapperMain" not in args:
            continue
        cwd = Path(f"/proc/{pid}/cwd")
        if cwd.parent.exists():  # Linux
            try:
                if not cwd.resolve().is_relative_to(root.resolve()):
                    continue
            except OSError:
                continue
        return True
    return False


def main() -> int:
    event = json.load(sys.stdin)
    is_main_stop = event.get("hook_event_name", "Stop" if not event.get("agent_id") else "") == "Stop"
    if is_main_stop and os.environ.get("PIANO_ANCHOR_STOP_HOOK") == "subagent-only":
        return 0
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

    if other_build_running(root):
        return warn("another Gradle build is running here; skipped fastCheck.")

    failure = gradle(root, "fastCheck")
    state.write_text(f"fail {current} {caller}" if failure else f"pass {current}")
    if failure:
        print(f"./gradlew fastCheck fails. Fix it before finishing:\n{failure}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())

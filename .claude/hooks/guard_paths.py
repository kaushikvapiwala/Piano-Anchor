#!/usr/bin/env python3
"""PreToolUse hook (Edit/Write/NotebookEdit): deny file edits the project forbids.

Denied: paths outside the repo (except Claude's own ~/.claude/ files and temp dirs), `.git/` internals,
and `docs/generated/` (generated from code; regenerate instead of hand-editing).
"""
from __future__ import annotations

import json
import os
import sys
import tempfile
from pathlib import Path

ALLOWED_OUTSIDE = [Path.home() / ".claude", Path(tempfile.gettempdir()), Path("/tmp"), Path("/private/tmp")]


def inside(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root.resolve())
        return True
    except ValueError:
        return False


def reason(file_path: str, root: Path) -> str | None:
    path = Path(file_path).expanduser().resolve()
    if not inside(path, root):
        if any(inside(path, allowed) for allowed in ALLOWED_OUTSIDE):
            return None
        return f"{path} is outside the repository. Only edit files inside {root}."
    rel = path.relative_to(root.resolve()).as_posix()
    if rel == ".git" or rel.startswith(".git/"):
        return "Don't edit .git internals; use git commands."
    if rel.startswith("docs/generated/"):
        return "docs/generated/ is generated from code. Change the source and regenerate instead."
    return None


def main() -> int:
    event = json.load(sys.stdin)
    tool_input = event.get("tool_input", {})
    file_path = tool_input.get("file_path") or tool_input.get("notebook_path")
    if not file_path:
        return 0
    root = Path(os.environ.get("CLAUDE_PROJECT_DIR") or event.get("cwd") or ".")
    why = reason(file_path, root)
    if why:
        decision = {"hookEventName": "PreToolUse", "permissionDecision": "deny", "permissionDecisionReason": why}
        print(json.dumps({"hookSpecificOutput": decision}))
    return 0


if __name__ == "__main__":
    sys.exit(main())

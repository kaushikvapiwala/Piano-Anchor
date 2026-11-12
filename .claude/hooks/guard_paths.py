#!/usr/bin/env python3
"""PreToolUse hook (Edit/Write/NotebookEdit): deny file edits the project forbids.

Denied: paths outside the repo (except Claude's own ~/.claude/ files and temp dirs), `.git/` internals,
and `docs/generated/` (generated from code; regenerate instead of hand-editing).
Asks the user first: edits to hook or settings files, so an agent can't quietly switch its own checks off.
Path checks ignore case (macOS and Windows file systems do).
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


def under(rel: str, prefix: str) -> bool:
    rel = rel.lower()
    return rel == prefix.rstrip("/") or rel.startswith(prefix)


def protects_hooks(path: Path, rel: str | None) -> bool:
    if rel is not None:
        return under(rel, ".claude/hooks/") or rel.lower().startswith(".claude/settings")
    claude_home = Path.home() / ".claude"
    return inside(path, claude_home) and path.parent == claude_home.resolve() and path.name.startswith("settings")


def decide(file_path: str, root: Path) -> tuple[str, str] | None:
    """("deny" | "ask", reason) for a guarded path, else None."""
    path = Path(file_path).expanduser().resolve()
    rel = path.relative_to(root.resolve()).as_posix() if inside(path, root) else None
    if protects_hooks(path, rel):
        return "ask", "This edits hook or Claude settings files that enforce project checks. Confirm the change."
    if rel is None:
        if any(inside(path, allowed) for allowed in ALLOWED_OUTSIDE):
            return None
        return "deny", f"{path} is outside the repository. Only edit files inside {root}."
    if under(rel, ".git/"):
        return "deny", "Don't edit .git internals; use git commands."
    if under(rel, "docs/generated/"):
        return "deny", "docs/generated/ is generated from code. Change the source and regenerate instead."
    return None


def main() -> int:
    event = json.load(sys.stdin)
    tool_input = event.get("tool_input", {})
    file_path = tool_input.get("file_path") or tool_input.get("notebook_path")
    if not file_path:
        return 0
    root = Path(os.environ.get("CLAUDE_PROJECT_DIR") or event.get("cwd") or ".")
    verdict = decide(file_path, root)
    if verdict:
        decision, why = verdict
        output = {"hookEventName": "PreToolUse", "permissionDecision": decision, "permissionDecisionReason": why}
        print(json.dumps({"hookSpecificOutput": output}))
    return 0


if __name__ == "__main__":
    sys.exit(main())

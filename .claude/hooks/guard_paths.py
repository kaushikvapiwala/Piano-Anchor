#!/usr/bin/env python3
"""PreToolUse hook (Edit/Write/NotebookEdit): deny file edits the project forbids.

Denied: paths outside the repo (except Claude's own ~/.claude/ files and temp dirs), `.git/` internals,
and `docs/generated/` (generated from code; regenerate instead of hand-editing).
Asks the user first: edits to hook or Claude settings files, so an agent can't quietly switch its own
checks off. Repo-relative checks ignore case (macOS and Windows file systems do).
"""
from __future__ import annotations

import json
import sys
import tempfile
from pathlib import Path

from hooklib import project_root

CLAUDE_HOME = (Path.home() / ".claude").resolve()
ALLOWED_OUTSIDE = [CLAUDE_HOME, Path(tempfile.gettempdir()).resolve(), Path("/tmp").resolve()]
DENIED = {
    ".git/": "Don't edit .git internals; use git commands.",
    "docs/generated/": "docs/generated/ is generated from code. Change the source and regenerate instead.",
}
HOOK_FILES = (".claude/hooks/", ".claude/settings", ".githooks/")
ASK_HOOKS = "This edits hook or Claude settings files that enforce project checks. Confirm the change."


def decide(file_path: str, root: Path) -> tuple[str, str] | None:
    """("deny" | "ask", reason) for a guarded path, else None. `root` must be resolved."""
    path = Path(file_path).expanduser().resolve()
    if not path.is_relative_to(root):
        if path.parent == CLAUDE_HOME and path.name.startswith("settings"):
            return "ask", ASK_HOOKS
        if any(path.is_relative_to(allowed) for allowed in ALLOWED_OUTSIDE):
            return None
        return "deny", f"{path} is outside the repository. Only edit files inside {root}."
    rel = path.relative_to(root).as_posix().lower() + ("/" if path.is_dir() else "")
    for prefix, why in DENIED.items():
        if rel.startswith(prefix) or rel == prefix.rstrip("/"):
            return "deny", why
    if rel.startswith(HOOK_FILES):
        return "ask", ASK_HOOKS
    return None


def main() -> int:
    event = json.load(sys.stdin)
    tool_input = event.get("tool_input", {})
    file_path = tool_input.get("file_path") or tool_input.get("notebook_path")
    verdict = decide(file_path, project_root(event)) if file_path else None
    if verdict:
        decision, why = verdict
        output = {"hookEventName": "PreToolUse", "permissionDecision": decision, "permissionDecisionReason": why}
        print(json.dumps({"hookSpecificOutput": output}))
    return 0


if __name__ == "__main__":
    sys.exit(main())

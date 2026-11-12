#!/usr/bin/env python3
"""PostToolUse hook (Edit/Write): format an edited Kotlin file with Spotless (ktlint), like an IDE save.

Uses Spotless's IDE hook (`-PspotlessIdeHook`), which formats only that file. Formatting failures (e.g. a
syntax error) are reported back to Claude (exit 2) so it can fix them right away.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from hooklib import gradle, project_root


def main() -> int:
    event = json.load(sys.stdin)
    file_path = event.get("tool_input", {}).get("file_path")
    if not file_path:
        return 0
    root = project_root(event)
    path = Path(file_path).resolve()
    if path.suffix not in {".kt", ".kts"} or not path.is_relative_to(root) or not path.exists():
        return 0
    failure = gradle(root, "spotlessApply", f"-PspotlessIdeHook={path}")
    if failure:
        print(f"Formatting {path.name} failed:\n{failure}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())

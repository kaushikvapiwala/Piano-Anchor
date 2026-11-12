#!/usr/bin/env python3
"""PostToolUse hook (Edit/Write): format an edited Kotlin file with Spotless (ktlint), like an IDE save.

Uses Spotless's IDE hook (`-PspotlessIdeHook`), which formats only that file. Formatting failures (e.g. a
syntax error) are reported back to Claude (exit 2) so it can fix them right away.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

from gradle_output import failure_summary


def main() -> int:
    event = json.load(sys.stdin)
    file_path = event.get("tool_input", {}).get("file_path", "")
    root = Path(os.environ.get("CLAUDE_PROJECT_DIR") or event.get("cwd") or ".").resolve()
    path = Path(file_path).resolve() if file_path else None
    if path is None or path.suffix not in {".kt", ".kts"} or root not in path.parents or not path.exists():
        return 0
    result = subprocess.run(
        ["./gradlew", "--quiet", "spotlessApply", f"-PspotlessIdeHook={path}"],
        cwd=root,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print(f"Formatting {path.name} failed:\n{failure_summary(result.stdout + result.stderr)}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())

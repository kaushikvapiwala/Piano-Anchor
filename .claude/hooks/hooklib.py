"""Shared helpers for the Claude Code hooks in this directory."""
from __future__ import annotations

import os
import re
import subprocess
from pathlib import Path

WHAT_WENT_WRONG = re.compile(r"\* What went wrong:\n(.*?)(?:\n\* Try:|\Z)", re.DOTALL)


def project_root(event: dict) -> Path:
    """The repository root: Claude's project dir, else the hook's working directory."""
    return Path(os.environ.get("CLAUDE_PROJECT_DIR") or event.get("cwd") or ".").resolve()


def failure_summary(output: str, limit: int = 4000) -> str:
    """Gradle's "What went wrong" sections (one per failed task), else the tail of the output."""
    sections = [s.strip() for s in WHAT_WENT_WRONG.findall(output)]
    return ("\n\n".join(sections) if sections else output)[-limit:]


def gradle(root: Path, *args: str) -> str | None:
    """Runs `./gradlew --quiet <args>`; returns None on success, else a short failure summary."""
    result = subprocess.run(["./gradlew", "--quiet", *args], cwd=root, capture_output=True, text=True)
    return None if result.returncode == 0 else failure_summary(result.stdout + result.stderr)

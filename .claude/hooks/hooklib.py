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


FAILURE_REPORT = re.compile(r"^FAILURE:", re.MULTILINE)
# JVM environment echoes and detekt report paths: printed even under --quiet, never the cause.
NOISE = re.compile(r"Picked up \w+:.*\n?|^Successfully generated \w+ at .*\n?", re.MULTILINE)


def failure_summary(output: str, limit: int = 4000) -> str:
    """Gradle's "What went wrong" sections (one per failed task), then what the quiet build printed before its
    failure report (failed tests, compiler errors), cut to `limit`; without sections, the tail of the output."""
    sections = [s.strip() for s in WHAT_WENT_WRONG.findall(output)]
    if not sections:
        return output[-limit:]
    before, *report = FAILURE_REPORT.split(output, maxsplit=1)
    details = NOISE.sub("", before).strip() if report else ""
    return "\n\n".join([*sections, details] if details else sections)[:limit]


def gradle(root: Path, *args: str) -> str | None:
    """Runs `./gradlew --quiet <args>`; returns None on success, else a short failure summary."""
    result = subprocess.run(["./gradlew", "--quiet", *args], cwd=root, capture_output=True, text=True)
    return None if result.returncode == 0 else failure_summary(result.stdout + result.stderr)

"""Shared helper for hooks: shorten Gradle output to what Claude needs to fix a failure."""
from __future__ import annotations

import re

WHAT_WENT_WRONG = re.compile(r"\* What went wrong:\n(.*?)(?:\n\* Try:|\Z)", re.DOTALL)


def failure_summary(output: str, limit: int = 4000) -> str:
    """Gradle's "What went wrong" sections (one per failed task), else the tail of the output."""
    sections = [s.strip() for s in WHAT_WENT_WRONG.findall(output)]
    return ("\n\n".join(sections) if sections else output)[-limit:]

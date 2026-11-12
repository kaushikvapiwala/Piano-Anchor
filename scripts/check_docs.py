#!/usr/bin/env python3
"""Docs check (run by `./gradlew docsCheck`, part of fastCheck).

Fails when documentation drifts from the code:
1. A repo path written in backticks in a Markdown file does not exist.
2. A relative Markdown link points to a missing file.
3. docs/ARCHITECTURE.md and settings.gradle.kts disagree on the list of Gradle modules.

Only files git knows about (tracked, or new and not ignored) count, so the result is the same on a
developer machine and on a clean CI checkout. Gitignored names (e.g. `local.properties`) are skipped,
and fenced code blocks are ignored.
"""
from __future__ import annotations

import posixpath
import re
import subprocess
import sys
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parent.parent
# A token is a path if it uses only path characters and has a "/" or a doc-relevant file extension.
# Slashed tokens must also start with a top-level repo entry, so branch names like `feat/x` are skipped.
PATH_LIKE = re.compile(r"^[\w.-]+(/[\w.-]+)+$|^[\w.-]+\.(md|kts|toml|py|yml|yaml|properties)$")
PATH_TOKEN = re.compile(r"`([^`\s]+)`")
MD_LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
FENCE = re.compile(r"^```.*?^```", re.MULTILINE | re.DOTALL)
MODULE = re.compile(r"`(:[\w:-]+)`")


def git(*args: str, stdin: str = "", ok_codes: tuple[int, ...] = (0,)) -> list[str]:
    result = subprocess.run(["git", *args], cwd=ROOT, input=stdin, capture_output=True, text=True)
    if result.returncode not in ok_codes:
        raise subprocess.CalledProcessError(result.returncode, result.args, result.stdout, result.stderr)
    return [line for line in result.stdout.splitlines() if line]


def resolve(base: str, path: str) -> str | None:
    """Repo-relative normalized path, or None if it leaves the repo."""
    joined = posixpath.normpath(posixpath.join(base, path))
    return None if joined == ".." or joined.startswith("../") else joined


def check_references(files: list[str]) -> list[str]:
    paths = set(files) | {str(p) for f in files for p in PurePosixPath(f).parents if str(p) != "."}
    docs = [f for f in files if f.endswith(".md")]
    texts = {rel: FENCE.sub("", (ROOT / rel).read_text(encoding="utf-8")) for rel in docs}
    # Backticked (token, path) pairs per doc; only path-like ones are checked.
    refs = {
        rel: [(t, path) for t in PATH_TOKEN.findall(text) if PATH_LIKE.match(path := t.split("#")[0].rstrip("/.,"))]
        for rel, text in texts.items()
    }
    # check-ignore exits 1 when nothing is ignored, and aborts the whole batch on a path outside the repo (e.g.
    # `../Research/`), so those are left out. Each token is also sent with a trailing "/" so directory-only
    # patterns (`dir/`) match even when the directory is absent (CI).
    inside = {resolve("", path.strip("/")) for pairs in refs.values() for _, path in pairs} - {None}
    candidates = [form for t in inside for form in (t, t + "/")]
    ignored = {line.rstrip("/") for line in git("check-ignore", "--stdin", ok_codes=(0, 1), stdin="\n".join(candidates))}

    roots = {f.split("/")[0] for f in files}
    errors = []
    for rel, text in texts.items():
        doc_dir = posixpath.dirname(rel)
        for token, path in refs[rel]:
            if resolve("", path.strip("/")) in ignored:
                continue
            if "/" in path and path.split("/")[0] not in roots:
                continue
            bases = [""] if "/" in path else ["", doc_dir]  # bare file names may be relative to the doc
            if not any(resolve(b, path.strip("/")) in paths for b in bases):
                errors.append(f"{rel}: backticked path `{token}` does not exist")
        for target in MD_LINK.findall(text):
            path = target.split("#")[0]
            if not path or target.startswith(("http://", "https://", "mailto:")):
                continue
            base = "" if path.startswith("/") else doc_dir
            if resolve(base, path.lstrip("/")) not in paths:
                errors.append(f"{rel}: link target '{target}' does not exist in the repo")
    return errors


def check_modules() -> list[str]:
    settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    included = {m for call in re.findall(r"include\(([^)]*)\)", settings) for m in re.findall(r'"(:[\w:-]+)"', call)}
    documented = set(MODULE.findall((ROOT / "docs/ARCHITECTURE.md").read_text(encoding="utf-8")))
    return [
        f"docs/ARCHITECTURE.md: module `{m}` from settings.gradle.kts is not documented" for m in sorted(included - documented)
    ] + [f"docs/ARCHITECTURE.md: module `{m}` is not in settings.gradle.kts" for m in sorted(documented - included)]


def main() -> int:
    errors = check_references(git("ls-files", "--cached", "--others", "--exclude-standard")) + check_modules()
    for error in errors:
        print(f"docsCheck: {error}", file=sys.stderr)
    if errors:
        print(f"docsCheck: {len(errors)} problem(s). Fix the docs or the reference.", file=sys.stderr)
        return 1
    print("docsCheck: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())

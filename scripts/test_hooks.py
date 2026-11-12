#!/usr/bin/env python3
"""Tests for the git hooks (.githooks/), the gitlink guard and the Claude Code hooks (.claude/hooks/).

Run by `./gradlew hooksTest` (part of fastCheck). Git hooks run against throwaway repositories.
"""
from __future__ import annotations

import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
# Inside a git hook (pre-commit runs fastCheck), git exports GIT_DIR, GIT_INDEX_FILE etc. Drop them so the
# throwaway repositories below never touch the real repository.
for _name in [n for n in os.environ if n.startswith("GIT_")]:
    del os.environ[_name]
sys.path.insert(0, str(ROOT / ".claude" / "hooks"))

import guard_paths  # noqa: E402
import stop_fastcheck  # noqa: E402
from gradle_output import failure_summary  # noqa: E402


def run(*args: str, cwd: Path) -> subprocess.CompletedProcess:
    return subprocess.run(args, cwd=cwd, capture_output=True, text=True)


class TempRepo(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.repo = Path(self.tmp.name).resolve()
        run("git", "init", "-q", cwd=self.repo)
        run("git", "config", "user.name", "Ada Test", cwd=self.repo)
        run("git", "config", "user.email", "ada@example.com", cwd=self.repo)
        # Isolate from the developer's global git config (signing, global hooks).
        run("git", "config", "commit.gpgsign", "false", cwd=self.repo)
        run("git", "config", "core.hooksPath", ".no-hooks", cwd=self.repo)

    def tearDown(self) -> None:
        self.tmp.cleanup()


class CommitMsgHookTest(TempRepo):
    def check(self, message: str) -> subprocess.CompletedProcess:
        msg = self.repo / "MSG"
        msg.write_text(message)
        return run(str(ROOT / ".githooks" / "commit-msg"), str(msg), cwd=self.repo)

    def test_accepts_sign_off_matching_the_author(self) -> None:
        self.assertEqual(self.check("feat: x\n\nSigned-off-by: Ada Test <ada@example.com>\n").returncode, 0)

    def test_rejects_missing_sign_off(self) -> None:
        result = self.check("feat: x\n")
        self.assertEqual(result.returncode, 1)
        self.assertIn("git commit -s", result.stderr)

    def test_rejects_sign_off_by_someone_else(self) -> None:
        self.assertEqual(self.check("feat: x\n\nSigned-off-by: Bob <bob@example.com>\n").returncode, 1)


class GitlinkGuardTest(TempRepo):
    def guard(self) -> subprocess.CompletedProcess:
        return run(str(ROOT / "scripts" / "check_no_gitlinks.sh"), cwd=self.repo)

    def test_passes_without_gitlinks(self) -> None:
        (self.repo / "a.txt").write_text("a")
        run("git", "add", "a.txt", cwd=self.repo)
        self.assertEqual(self.guard().returncode, 0)

    def test_fails_when_a_nested_repository_is_staged(self) -> None:
        nested = self.repo / "nested"
        nested.mkdir()
        run("git", "init", "-q", cwd=nested)
        run("git", "-c", "user.name=A", "-c", "user.email=a@b", "commit", "-q", "--allow-empty", "-m", "x", cwd=nested)
        run("git", "add", "nested", cwd=self.repo)
        result = self.guard()
        self.assertEqual(result.returncode, 1)
        self.assertIn("nested", result.stderr)


class GuardPathsTest(unittest.TestCase):
    root = ROOT

    def reason(self, path: str) -> str | None:
        return guard_paths.reason(path, self.root)

    def test_allows_repo_files(self) -> None:
        self.assertIsNone(self.reason(str(self.root / "README.md")))

    def test_allows_claude_home_and_temp_files(self) -> None:
        self.assertIsNone(self.reason(str(Path.home() / ".claude" / "plans" / "p.md")))
        self.assertIsNone(self.reason(os.path.join(tempfile.gettempdir(), "x.txt")))

    def test_denies_files_outside_the_repo(self) -> None:
        self.assertIn("outside the repository", self.reason(str(self.root.parent / "other.md")))
        self.assertIn("outside the repository", self.reason(str(self.root / ".." / "escape.md")))

    def test_denies_git_internals_and_generated_docs(self) -> None:
        self.assertIn(".git", self.reason(str(self.root / ".git" / "config")))
        self.assertIn("generated", self.reason(str(self.root / "docs" / "generated" / "ports.md")))

    def test_main_prints_a_deny_decision(self) -> None:
        event = json.dumps({"tool_input": {"file_path": "/etc/hosts"}})
        out = io.StringIO()
        with mock.patch.dict(os.environ, {"CLAUDE_PROJECT_DIR": str(self.root)}):
            with mock.patch("sys.stdin", io.StringIO(event)), redirect_stdout(out):
                self.assertEqual(guard_paths.main(), 0)
        self.assertEqual(json.loads(out.getvalue())["hookSpecificOutput"]["permissionDecision"], "deny")

    def test_main_ignores_tools_without_a_path(self) -> None:
        out = io.StringIO()
        with mock.patch("sys.stdin", io.StringIO('{"tool_input": {}}')), redirect_stdout(out):
            self.assertEqual(guard_paths.main(), 0)
        self.assertEqual(out.getvalue(), "")


class StopFastCheckTest(TempRepo):
    """Uses a fake gradlew that fails while a file named FAIL exists and counts its runs."""

    def setUp(self) -> None:
        super().setUp()
        gradlew = self.repo / "gradlew"
        gradlew.write_text(
            "#!/bin/sh\necho run >> runs.log\n"
            "if [ -f FAIL ]; then printf '* What went wrong:\\nboom\\n* Try:\\n'; exit 1; fi\n"
        )
        gradlew.chmod(0o755)
        (self.repo / ".gitignore").write_text("runs.log\n")
        run("git", "add", ".", cwd=self.repo)
        run("git", "commit", "-q", "-m", "init", cwd=self.repo)

    def stop(self, active: bool = False) -> tuple[int, str]:
        event = json.dumps({"stop_hook_active": active})
        err = io.StringIO()
        with mock.patch.dict(os.environ, {"CLAUDE_PROJECT_DIR": str(self.repo)}):
            with mock.patch("sys.stdin", io.StringIO(event)), mock.patch("sys.stderr", err), redirect_stdout(io.StringIO()):
                return stop_fastcheck.main(), err.getvalue()

    def runs(self) -> int:
        log = self.repo / "runs.log"
        return len(log.read_text().splitlines()) if log.exists() else 0

    def test_passing_check_is_not_rerun_until_files_change(self) -> None:
        self.assertEqual(self.stop()[0], 0)
        self.assertEqual(self.stop()[0], 0)
        self.assertEqual(self.runs(), 1)
        (self.repo / "new.txt").write_text("x")
        self.assertEqual(self.stop()[0], 0)
        self.assertEqual(self.runs(), 2)

    def test_failing_check_blocks_with_the_gradle_error(self) -> None:
        (self.repo / "FAIL").write_text("")
        code, err = self.stop()
        self.assertEqual(code, 2)
        self.assertIn("boom", err)

    def test_unchanged_retry_after_a_block_is_allowed(self) -> None:
        (self.repo / "FAIL").write_text("")
        self.assertEqual(self.stop()[0], 2)
        self.assertEqual(self.stop(active=True)[0], 0)
        self.assertEqual(self.runs(), 1)

    def test_changed_retry_after_a_block_reruns_the_check(self) -> None:
        (self.repo / "FAIL").write_text("")
        self.assertEqual(self.stop()[0], 2)
        (self.repo / "FAIL").unlink()
        self.assertEqual(self.stop(active=True)[0], 0)
        self.assertEqual(self.runs(), 2)


class FailureSummaryTest(unittest.TestCase):
    def test_keeps_only_what_went_wrong_sections(self) -> None:
        output = "noise\n* What went wrong:\nTask :a failed\n\n* Try:\n> help\n* What went wrong:\nTask :b failed\n"
        self.assertEqual(failure_summary(output), "Task :a failed\n\nTask :b failed")

    def test_falls_back_to_the_tail(self) -> None:
        self.assertEqual(failure_summary("x" * 10 + "end", limit=3), "end")


if __name__ == "__main__":
    unittest.main()

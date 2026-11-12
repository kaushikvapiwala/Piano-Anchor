#!/usr/bin/env python3
"""Tests for the git hooks (.githooks/), the gitlink guard, docsCheck and the Claude Code hooks and settings (.claude/).

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
sys.path[:0] = [str(ROOT / ".claude" / "hooks"), str(ROOT / "scripts")]

import check_docs  # noqa: E402
import guard_paths  # noqa: E402
import stop_fastcheck  # noqa: E402
from hooklib import failure_summary  # noqa: E402


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

    def test_skips_merge_commits(self) -> None:
        run("git", "commit", "-q", "--allow-empty", "-m", "init", cwd=self.repo)
        head = run("git", "rev-parse", "HEAD", cwd=self.repo).stdout.strip()
        (self.repo / ".git" / "MERGE_HEAD").write_text(head + "\n")
        self.assertEqual(self.check("Merge branch 'main'\n").returncode, 0)

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
        verdict = guard_paths.decide(path, self.root.resolve())
        return verdict[1] if verdict and verdict[0] == "deny" else None

    def asks(self, path: str) -> bool:
        verdict = guard_paths.decide(path, self.root.resolve())
        return verdict is not None and verdict[0] == "ask"

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

    def test_git_and_generated_checks_ignore_case(self) -> None:
        self.assertIsNotNone(self.reason(str(self.root / ".GIT" / "config")))
        self.assertIsNotNone(self.reason(str(self.root / "docs" / "Generated" / "ports.md")))

    def test_asks_before_editing_hooks_or_settings(self) -> None:
        self.assertTrue(self.asks(str(self.root / ".claude" / "settings.json")))
        self.assertTrue(self.asks(str(self.root / ".claude" / "hooks" / "guard_paths.py")))
        self.assertTrue(self.asks(str(self.root / ".githooks" / "pre-commit")))
        self.assertTrue(self.asks(str(Path.home() / ".claude" / "settings.json")))
        self.assertFalse(self.asks(str(Path.home() / ".claude" / "plans" / "settings.md")))
        self.assertFalse(self.asks(str(self.root / "README.md")))

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

    def stop(self, event: str = "{}") -> tuple[int, str]:
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

    def test_unchanged_failing_tree_is_reported_once(self) -> None:
        (self.repo / "FAIL").write_text("")
        self.assertEqual(self.stop()[0], 2)
        self.assertEqual(self.stop()[0], 0)
        self.assertEqual(self.stop()[0], 0)  # a later turn
        self.assertEqual(self.runs(), 1)

    def test_commit_of_a_checked_tree_is_not_rechecked(self) -> None:
        (self.repo / "new.txt").write_text("x")
        self.assertEqual(self.stop()[0], 0)
        run("git", "add", "new.txt", cwd=self.repo)
        run("git", "commit", "-q", "-m", "add", cwd=self.repo)
        self.assertEqual(self.stop()[0], 0)
        self.assertEqual(self.runs(), 1)

    def test_failure_reported_to_a_subagent_still_blocks_the_main_session(self) -> None:
        (self.repo / "FAIL").write_text("")
        subagent = '{"agent_id": "a1", "agent_type": "implementer"}'
        self.assertEqual(self.stop(subagent)[0], 2)
        self.assertEqual(self.stop(subagent)[0], 0)
        self.assertEqual(self.stop()[0], 2)

    def test_allows_stop_outside_a_git_repository(self) -> None:
        with tempfile.TemporaryDirectory() as plain:
            with mock.patch.dict(os.environ, {"CLAUDE_PROJECT_DIR": plain}):
                out = io.StringIO()
                with mock.patch("sys.stdin", io.StringIO("{}")), redirect_stdout(out):
                    self.assertEqual(stop_fastcheck.main(), 0)
        self.assertIn("no usable git repository", out.getvalue())

    def test_changed_retry_after_a_block_reruns_the_check(self) -> None:
        (self.repo / "FAIL").write_text("")
        self.assertEqual(self.stop()[0], 2)
        (self.repo / "FAIL").unlink()
        self.assertEqual(self.stop()[0], 0)
        self.assertEqual(self.runs(), 2)


class SettingsTest(unittest.TestCase):
    """The implementer subagent must face the same fastCheck gate as the main session; scouts must not."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.hooks = json.loads((ROOT / ".claude" / "settings.json").read_text())["hooks"]

    def test_implementer_subagent_stop_runs_the_stop_hook(self) -> None:
        (entry,) = self.hooks["SubagentStop"]
        self.assertEqual(entry["hooks"], self.hooks["Stop"][0]["hooks"])
        self.assertRegex("implementer", entry["matcher"])
        self.assertNotRegex("Explore", entry["matcher"])
        self.assertNotRegex("general-purpose", entry["matcher"])

    def test_implementer_agent_file_exists(self) -> None:
        agent = (ROOT / ".claude" / "agents" / "implementer.md").read_text()
        self.assertIn("name: implementer\n", agent)
        self.assertIn("model: sonnet\n", agent)


class DocsCheckTest(TempRepo):
    def problems(self, doc: str) -> list[str]:
        (self.repo / ".gitignore").write_text("secret.properties\n")
        (self.repo / "secret.properties").write_text("")
        (self.repo / "DOC.md").write_text(doc)
        with mock.patch.object(check_docs, "ROOT", self.repo):
            return check_docs.check_references(check_docs.git("ls-files", "--others", "--exclude-standard"))

    def test_skips_ignored_paths_even_next_to_a_path_outside_the_repo(self) -> None:
        # git check-ignore aborts the whole batch on `../x`, which once made every ignored path "missing".
        self.assertEqual(self.problems("`../Research/` and `secret.properties`\n"), [])

    def test_reports_a_missing_path(self) -> None:
        self.assertEqual(self.problems("`missing.md`\n"), ["DOC.md: backticked path `missing.md` does not exist"])


class FailureSummaryTest(unittest.TestCase):
    def test_keeps_only_what_went_wrong_sections(self) -> None:
        output = "noise\n* What went wrong:\nTask :a failed\n\n* Try:\n> help\n* What went wrong:\nTask :b failed\n"
        self.assertEqual(failure_summary(output), "Task :a failed\n\nTask :b failed")

    def test_falls_back_to_the_tail(self) -> None:
        self.assertEqual(failure_summary("x" * 10 + "end", limit=3), "end")


if __name__ == "__main__":
    unittest.main()

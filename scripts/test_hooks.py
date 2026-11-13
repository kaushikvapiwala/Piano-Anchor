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

    def stop(self, event: str = "{}", env: dict[str, str] | None = None, build_running: bool = False) -> tuple[int, str]:
        err, out = io.StringIO(), io.StringIO()
        self.stdout = out
        with mock.patch.dict(os.environ, {"CLAUDE_PROJECT_DIR": str(self.repo), **(env or {})}):
            with mock.patch("sys.stdin", io.StringIO(event)), mock.patch("sys.stderr", err), redirect_stdout(out):
                with mock.patch.object(stop_fastcheck, "other_build_running", return_value=build_running):
                    return stop_fastcheck.main(), err.getvalue()

    def state_file(self) -> Path:
        return self.repo / ".git" / "claude-fastcheck"

    def test_subagent_only_skips_the_main_session_stop(self) -> None:
        env = {"PIANO_ANCHOR_STOP_HOOK": "subagent-only"}
        for event in ("{}", '{"hook_event_name": "Stop"}'):
            self.assertEqual(self.stop(event, env)[0], 0)
        self.assertEqual(self.runs(), 0)
        self.assertFalse(self.state_file().exists())

    def test_subagent_only_still_checks_subagent_stops(self) -> None:
        env = {"PIANO_ANCHOR_STOP_HOOK": "subagent-only"}
        for event in ('{"agent_id": "a1"}', '{"hook_event_name": "SubagentStop", "agent_id": "a1"}'):
            self.assertEqual(self.stop(event, env)[0], 0)
        self.assertEqual(self.runs(), 1)
        self.assertTrue(self.state_file().exists())

    def test_running_gradle_build_skips_the_check_without_state(self) -> None:
        self.assertEqual(self.stop(build_running=True)[0], 0)
        self.assertIn("another Gradle build is running", self.stdout.getvalue())
        self.assertEqual(self.runs(), 0)
        self.assertFalse(self.state_file().exists())
        self.assertEqual(self.stop()[0], 0)
        self.assertEqual(self.runs(), 1)

    def test_detector_ignores_idle_daemons_and_our_own_tree(self) -> None:
        root = Path("/nonexistent-root")
        ps = f"{os.getpid()} java GradleWrapperMain\n77 java -cp x GradleDaemon\n"
        done = subprocess.CompletedProcess([], 0, stdout=ps)
        with mock.patch("subprocess.run", return_value=done):
            self.assertFalse(stop_fastcheck.other_build_running(root))

    def test_detector_finds_a_wrapper_client(self) -> None:
        ps = "99999991 java -classpath gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain fastCheck\n"
        with mock.patch("subprocess.run", return_value=subprocess.CompletedProcess([], 0, stdout=ps)):
            with mock.patch.object(stop_fastcheck, "own_ancestors", return_value=set()):
                with mock.patch.object(stop_fastcheck, "runs_in", return_value=True):
                    self.assertTrue(stop_fastcheck.other_build_running(self.repo))
                with mock.patch.object(stop_fastcheck, "runs_in", return_value=False):
                    self.assertFalse(stop_fastcheck.other_build_running(self.repo))

    def test_runs_in_checks_the_process_directory_where_it_can(self) -> None:
        # The test process itself works in a temp dir, never inside a fresh repo root elsewhere.
        if Path("/proc/self").exists():
            self.assertFalse(stop_fastcheck.runs_in(os.getpid(), Path("/nonexistent-root")))
            self.assertTrue(stop_fastcheck.runs_in(os.getpid(), Path(os.getcwd())))
        else:
            self.assertTrue(stop_fastcheck.runs_in(os.getpid(), Path("/nonexistent-root")))

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


class PrePushHookTest(TempRepo):
    """Uses a fake gradlew that records each run, so no real build starts."""

    ZERO = "0" * 40
    SHA = "a" * 40

    def setUp(self) -> None:
        super().setUp()
        gradlew = self.repo / "gradlew"
        gradlew.write_text("#!/bin/sh\necho \"$@\" >> runs.log\n")
        gradlew.chmod(0o755)

    def push(self, *lines: str) -> int:
        stdin = "".join(line + "\n" for line in lines)
        result = subprocess.run(
            [str(ROOT / ".githooks" / "pre-push"), "origin", "url"], cwd=self.repo, input=stdin, text=True, capture_output=True
        )
        return result.returncode

    def builds(self) -> list[str]:
        log = self.repo / "runs.log"
        return log.read_text().splitlines() if log.exists() else []

    def test_runs_ci_build_when_pushing_a_branch(self) -> None:
        self.assertEqual(self.push(f"refs/heads/x {self.SHA} refs/heads/x {self.ZERO}"), 0)
        self.assertEqual(self.builds(), ["--quiet ciBuild"])

    def test_skips_the_build_when_every_ref_is_a_deletion(self) -> None:
        self.assertEqual(self.push(f"(delete) {self.ZERO} refs/heads/x {self.SHA}", f"(delete) {self.ZERO} refs/heads/y {self.SHA}"), 0)
        self.assertEqual(self.builds(), [])

    def test_runs_the_build_when_a_deletion_is_mixed_with_a_push(self) -> None:
        self.push(f"(delete) {self.ZERO} refs/heads/x {self.SHA}", f"refs/heads/y {self.SHA} refs/heads/y {self.ZERO}")
        self.assertEqual(self.builds(), ["--quiet ciBuild"])


class CloudSetupTest(TempRepo):
    script = str(ROOT / "scripts" / "cloud_setup.sh")

    def setup_run(self, **env: str) -> subprocess.CompletedProcess:
        base = {k: v for k, v in os.environ.items() if k != "CLAUDE_CODE_REMOTE"}
        base.update(HOME=str(self.repo), **env)
        return subprocess.run([self.script], cwd=self.repo, env=base, capture_output=True, text=True, timeout=60)

    def test_is_registered_as_a_session_start_hook(self) -> None:
        hooks = json.loads((ROOT / ".claude" / "settings.json").read_text())["hooks"]
        commands = [h["command"] for entry in hooks["SessionStart"] for h in entry["hooks"]]
        self.assertTrue(any("scripts/cloud_setup.sh" in c for c in commands))

    def test_does_nothing_outside_the_cloud(self) -> None:
        before = (ROOT / "local.properties").read_bytes() if (ROOT / "local.properties").exists() else None
        result = self.setup_run()
        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stdout + result.stderr, "")
        self.assertFalse((self.repo / "android-sdk").exists())
        after = (ROOT / "local.properties").read_bytes() if (ROOT / "local.properties").exists() else None
        self.assertEqual(before, after)

    def test_gradle_retry_settings_are_written_once(self) -> None:
        for _ in range(2):
            self.setup_run(CLAUDE_CODE_REMOTE="true", ANDROID_REPO_BASE="http://127.0.0.1:1")
        lines = (self.repo / ".gradle" / "gradle.properties").read_text().splitlines()
        self.assertEqual(
            lines,
            [
                "systemProp.org.gradle.internal.repository.max.retries=6",
                "systemProp.org.gradle.internal.repository.initial.backoff=2000",
            ],
        )

    def test_writes_the_maven_mirror_init_script_idempotently_before_any_download(self) -> None:
        init = self.repo / ".gradle" / "init.d" / "cloud-maven-mirror.init.gradle.kts"
        for _ in range(2):
            self.setup_run(CLAUDE_CODE_REMOTE="true", ANDROID_REPO_BASE="http://127.0.0.1:1")
            text = init.read_text()
            self.assertIn("https://maven-central.storage-download.googleapis.com/maven2/", text)
            self.assertIn("repo.maven.apache.org", text)
            self.assertIn("repo1.maven.org", text)
            init.write_text("stale")  # the second run must overwrite it

    def test_writes_no_init_script_outside_the_cloud(self) -> None:
        self.setup_run(ANDROID_REPO_BASE="http://127.0.0.1:1")
        self.assertFalse((self.repo / ".gradle" / "init.d").exists())

    def test_finds_the_jdk_when_java_prints_a_picked_up_line_first(self) -> None:
        bin_dir = self.repo / "bin"
        bin_dir.mkdir()
        java = bin_dir / "java"
        java.write_text(
            "#!/bin/sh\necho 'Picked up JAVA_TOOL_OPTIONS: -Xmx1g' >&2\n"
            "echo 'openjdk version \"21.0.2\" 2024-01-16' >&2\n"
        )
        java.chmod(0o755)
        path = f"{bin_dir}:{os.environ['PATH']}"
        result = self.setup_run(CLAUDE_CODE_REMOTE="true", ANDROID_REPO_BASE="http://127.0.0.1:1", PATH=path)
        self.assertNotIn("no JDK", result.stdout)
        java.write_text("#!/bin/sh\necho 'openjdk version \"11.0.2\"' >&2\n")
        result = self.setup_run(CLAUDE_CODE_REMOTE="true", ANDROID_REPO_BASE="http://127.0.0.1:1", PATH=path)
        self.assertIn("no JDK >= 17", result.stdout)

    def test_reports_a_download_failure_without_blocking_the_session(self) -> None:
        result = self.setup_run(CLAUDE_CODE_REMOTE="true", ANDROID_REPO_BASE="http://127.0.0.1:1")
        self.assertEqual(result.returncode, 0)
        self.assertIn("download of http://127.0.0.1:1/", result.stdout)
        self.assertIn("fastCheck will fail", result.stdout)
        self.assertFalse((self.repo / "android-sdk" / "cmdline-tools" / "latest").exists())


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

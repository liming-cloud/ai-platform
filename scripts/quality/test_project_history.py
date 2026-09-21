"""用真实 Git 对象验证历史定位，不把索引中的声明当作证据。"""

import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from scripts import project_governance as governance


class ProjectHistoryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git("init", "-q")
        self.git("config", "user.name", "治理测试")
        self.git("config", "user.email", "governance@example.invalid")
        self.original = "project/evidence/result.md"
        file = self.root / self.original
        file.parent.mkdir(parents=True)
        file.write_text("# 当时失败，仅验证登录切片\n")
        self.content = file.read_bytes()
        self.git("add", self.original)
        self.git("commit", "-qm", "doc: 固定测试证据")
        self.source = self.git("rev-parse", "HEAD").strip()
        self.entry = {"path": self.original, "action": "delete", "reason": "历史过程结果",
                      "sha256": hashlib.sha256(self.content).hexdigest(), "references": []}
        self.index = self.root / "docs/tasks/history-index.json"
        self.index.parent.mkdir(parents=True)
        self.write_index()
        file.unlink()

    def git(self, *args):
        return subprocess.run(["git", *args], cwd=self.root, check=True,
                              capture_output=True, text=True).stdout

    def write_index(self):
        self.index.write_text(json.dumps({"schemaVersion": 1, "sourceCommit": self.source,
                                         "files": [self.entry]}, ensure_ascii=False))

    def store(self):
        return governance.HistoryStore(self.root)

    def test_deleted_evidence_is_read_from_exact_git_object(self):
        self.assertEqual(self.content, self.store().read(self.original))
        self.assertFalse((self.root / self.original).exists())

    def test_changed_digest_cannot_manufacture_historical_evidence(self):
        self.entry["sha256"] = "0" * 64
        self.write_index()
        with self.assertRaisesRegex(governance.GovernanceError, "摘要"):
            self.store().read(self.original)

    def test_missing_commit_cannot_be_accepted(self):
        self.source = "0" * 40
        self.write_index()
        with self.assertRaisesRegex(governance.GovernanceError, "提交"):
            self.store().read(self.original)

    def test_missing_git_path_cannot_be_accepted(self):
        self.entry["path"] = "project/evidence/invented.md"
        self.write_index()
        with self.assertRaisesRegex(governance.GovernanceError, "历史文件"):
            self.store().read(self.entry["path"])

    def test_current_evidence_remains_readable_without_history_substitution(self):
        current = self.root / "docs/tasks/GOV-0001/evidence.md"
        current.parent.mkdir()
        current.write_text("# 当前结果\n")
        self.assertEqual(current.read_bytes(), self.store().read(str(current.relative_to(self.root))))

    def test_duplicate_history_paths_are_rejected(self):
        data = json.loads(self.index.read_text())
        data["files"].append(self.entry)
        self.index.write_text(json.dumps(data))
        with self.assertRaisesRegex(governance.GovernanceError, "重复"):
            self.store()

    def test_paths_outside_repository_are_rejected(self):
        with self.assertRaisesRegex(governance.GovernanceError, "路径"):
            self.store().read("../outside.md")

    def test_move_target_cannot_read_a_symlink_outside_repository(self):
        outside = tempfile.TemporaryDirectory()
        self.addCleanup(outside.cleanup)
        target = Path(outside.name) / 'outside.md'
        target.write_bytes(self.content)
        destination = self.root / 'docs/tasks/moved.md'
        destination.symlink_to(target)
        self.entry.update(action='move', destination='docs/tasks/moved.md')
        self.write_index()
        with self.assertRaisesRegex(governance.GovernanceError, '路径'):
            self.store().verify()

    def test_verification_does_not_create_observation_or_cache_files(self):
        before = sorted(str(p.relative_to(self.root)) for p in self.root.rglob("*") if p.is_file())
        self.store().read(self.original)
        after = sorted(str(p.relative_to(self.root)) for p in self.root.rglob("*") if p.is_file())
        self.assertEqual(before, after)

    def test_real_cli_without_python_flags_does_not_create_bytecode(self):
        scripts = self.root / 'scripts'
        scripts.mkdir()
        source = Path(__file__).resolve().parents[1]
        for name in ('project_governance.py', 'project_history.py'):
            (scripts / name).write_bytes((source / name).read_bytes())
        before = {str(p.relative_to(self.root)) for p in self.root.rglob('*') if p.is_file()}
        result = subprocess.run([sys.executable, str(scripts / 'project_governance.py'),
                                 'history', self.original], cwd=self.root, capture_output=True, check=True)
        self.assertEqual(self.content, result.stdout)
        after = {str(p.relative_to(self.root)) for p in self.root.rglob('*') if p.is_file()}
        self.assertEqual(before, after)


class ControlPlaneMigrationTest(unittest.TestCase):
    def test_product_frontier_does_not_become_the_governance_task(self):
        root = Path(__file__).resolve().parents[2]
        state, _ = governance.verify(root)
        self.assertIn("productFrontier", state)
        self.assertIn("currentTask", state)
        self.assertTrue(state["productFrontier"].startswith("WP-"))

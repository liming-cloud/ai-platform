import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "project_governance.py"
SPEC = importlib.util.spec_from_file_location("project_governance", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ProjectGovernanceTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        for relative in (
            "project/work-packages",
            "project/evidence",
            "docs/adr",
        ):
            (self.root / relative).mkdir(parents=True, exist_ok=True)
        for relative in (
            "project/CHARTER.md",
            "project/ROADMAP.md",
            "project/DECISIONS.md",
            "project/TRACEABILITY.md",
            "project/RISKS.md",
            "project/evidence/INDEX.md",
            "docs/adr/ADR-0001-test.md",
            *MODULE.REQUIRED_DESIGN_FILES,
        ):
            (self.root / relative).parent.mkdir(parents=True, exist_ok=True)
            (self.root / relative).write_text("# 测试\n", encoding="utf-8")
        self.write_state(["WP-0001"])
        self.write_work_package("WP-0001", owner="后端")

    def tearDown(self):
        self.temp_dir.cleanup()

    def write_state(self, active, blocked=()):
        lines = [
            "schemaVersion: 1",
            "productVersion: foundation-v1",
            "currentMilestone: M0",
            f"currentFrontier: {(active or blocked)[0]}",
            "lastVerifiedCommit: 6969e5c6f4af7af2f9db1eb3cc447fd278b997d5",
            "activeWorkPackages:",
            *(f"  - {item}" for item in active),
            "blockedWorkPackages:",
            *(f"  - {item}" for item in blocked),
            "confirmedDecisions:",
            "  - ADR-0001",
            "openDecisions:",
            "  - HITL-0001",
            "latestEvidence:",
            "  - project/evidence/INDEX.md",
            "nextReviewAt: 2026-07-27",
            "updatedAt: 2026-07-26T00:00:00+08:00",
            "updatedBy: codex",
        ]
        (self.root / "project/STATE.yaml").write_text("\n".join(lines) + "\n", encoding="utf-8")

    def write_work_package(self, identifier, owner, status="active", evidence="pending", commit="pending"):
        sections = "\n".join(f"## {section}\n\n内容\n" for section in sorted(MODULE.REQUIRED_WORK_PACKAGE_SECTIONS))
        status_text = {"active": "进行中", "done": "已完成"}.get(status, status)
        text = f"""# {identifier}

| 字段 | 值 |
| --- | --- |
| ID | {identifier} |
| 状态 | {status_text} |
| 负责人 | {owner} |
| 分支 | codex/test |
| 里程碑 | M0 |
| ADR | ADR-0001 |
| 证据 | {evidence} |
| 提交 | {commit} |

{sections}
"""
        (self.root / "project/work-packages" / f"{identifier}.md").write_text(text, encoding="utf-8")

    def test_valid_control_plane_passes(self):
        state, packages = MODULE.verify(self.root)
        self.assertEqual("WP-0001", state["currentFrontier"])
        self.assertIn("WP-0001", packages)

    def test_missing_work_package_fails(self):
        self.write_state(["WP-0099"])
        with self.assertRaisesRegex(MODULE.GovernanceError, "不存在或无效的工作包"):
            MODULE.verify(self.root)

    def test_one_owner_cannot_have_multiple_active_packages(self):
        self.write_work_package("WP-0002", owner="后端")
        self.write_state(["WP-0001", "WP-0002"])
        with self.assertRaisesRegex(MODULE.GovernanceError, "多个进行中的工作包"):
            MODULE.verify(self.root)

    def test_done_package_requires_commit_bound_evidence(self):
        self.write_work_package("WP-0001", owner="后端", status="done")
        self.write_work_package("WP-0002", owner="产品")
        self.write_state(["WP-0002"])
        with self.assertRaisesRegex(MODULE.GovernanceError, "已完成但没有证据"):
            MODULE.verify(self.root)

    def test_m0_rejects_legacy_tracked_files(self):
        (self.root / ".git").mkdir()
        with (
            mock.patch.object(MODULE, "tracked_files", return_value={"backend/pom.xml"}),
            mock.patch.object(MODULE, "git_commit_exists", return_value=True),
            mock.patch.object(MODULE, "is_ancestor", return_value=True),
            self.assertRaisesRegex(MODULE.GovernanceError, "仍包含旧实现文件"),
        ):
            MODULE.verify(self.root)


if __name__ == "__main__":
    unittest.main()

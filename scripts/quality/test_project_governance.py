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
            *MODULE.REQUIRED_DESIGN_FILES,
        ):
            (self.root / relative).parent.mkdir(parents=True, exist_ok=True)
            (self.root / relative).write_text("# 测试\n", encoding="utf-8")
        layout_sections = "\n".join(
            f"## {index}. {section}\n\n内容\n"
            for index, section in enumerate(
                sorted(MODULE.REQUIRED_REPOSITORY_LAYOUT_SECTIONS),
                start=1,
            )
        )
        (
            self.root
            / "docs/architecture/foundation-repository-layout.md"
        ).write_text(layout_sections, encoding="utf-8")
        self.write_adr("ADR-0001")
        self.write_state(["WP-0001"])
        self.write_work_package("WP-0001", owner="后端")
        self.sync_decisions()

    def tearDown(self):
        self.temp_dir.cleanup()

    def write_state(self, active, blocked=(), confirmed=("ADR-0001",), evidence=None):
        evidence = evidence or ("project/evidence/INDEX.md",)
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
            *(f"  - {item}" for item in confirmed),
            "openDecisions:",
            "  - HITL-0001",
            "latestEvidence:",
            *(f"  - {item}" for item in evidence),
            "nextReviewAt: 2026-07-27",
            "updatedAt: 2026-07-26T00:00:00+08:00",
            "updatedBy: codex",
        ]
        (self.root / "project/STATE.yaml").write_text("\n".join(lines) + "\n", encoding="utf-8")

    def write_work_package(
        self,
        identifier,
        owner,
        status="active",
        evidence="pending",
        commit="pending",
        adrs=("ADR-0001",),
    ):
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
| ADR | {", ".join(adrs)} |
| 证据 | {evidence} |
| 提交 | {commit} |

{sections}
"""
        (self.root / "project/work-packages" / f"{identifier}.md").write_text(text, encoding="utf-8")

    def write_adr(self, identifier, status="已接受"):
        (self.root / "docs/adr" / f"{identifier}-test.md").write_text(
            f"# {identifier}：测试决策\n\n## 状态\n\n{status}\n",
            encoding="utf-8",
        )

    def sync_decisions(self):
        path = self.root / "project/DECISIONS.md"
        path.write_text(
            MODULE.render_decision_index(
                self.root,
                path.read_text(encoding="utf-8"),
            ),
            encoding="utf-8",
        )

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

    def test_done_package_rejects_stale_active_statement(self):
        self.write_work_package(
            "WP-0001",
            owner="后端",
            status="done",
            evidence="project/evidence/INDEX.md",
            commit="6969e5c6f4af7af2f9db1eb3cc447fd278b997d5",
        )
        self.write_work_package("WP-0002", owner="产品")
        self.write_state(["WP-0002"])
        package = self.root / "project/work-packages/WP-0001.md"
        package.write_text(
            package.read_text(encoding="utf-8")
            + "\n因此工作包保持进行中。\n",
            encoding="utf-8",
        )

        with self.assertRaisesRegex(MODULE.GovernanceError, "正文仍声明进行中"):
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

    def test_repository_layout_requires_all_boundary_sections(self):
        layout = self.root / "docs/architecture/foundation-repository-layout.md"
        layout.write_text("# 不完整目录设计\n", encoding="utf-8")
        with self.assertRaisesRegex(MODULE.GovernanceError, "详细目录规划缺少章节"):
            MODULE.verify(self.root)

    def test_backend_development_structure_is_required(self):
        (
            self.root
            / "docs/architecture/foundation-backend-development-structure.md"
        ).unlink()

        with self.assertRaisesRegex(MODULE.GovernanceError, "缺少基座设计文件"):
            MODULE.verify(self.root)

    def test_m1_requires_planned_context_modules(self):
        state = self.root / "project/STATE.yaml"
        state.write_text(
            state.read_text(encoding="utf-8").replace(
                "currentMilestone: M0",
                "currentMilestone: M1",
            ),
            encoding="utf-8",
        )

        with self.assertRaisesRegex(MODULE.GovernanceError, "M1 缺少限界上下文模块"):
            MODULE.verify(self.root)

    def test_latest_evidence_is_limited_to_eight_entries(self):
        evidence = tuple(f"project/evidence/evidence-{index}.md" for index in range(9))
        for relative in evidence:
            (self.root / relative).write_text("# 证据\n", encoding="utf-8")
        self.write_state(["WP-0001"], evidence=evidence)

        with self.assertRaisesRegex(MODULE.GovernanceError, "latestEvidence 最多保留 8 条"):
            MODULE.verify(self.root)

    def test_frontier_is_limited_to_twelve_active_adrs(self):
        adrs = tuple(f"ADR-{index:04d}" for index in range(1, 14))
        for identifier in adrs[1:]:
            self.write_adr(identifier)
        self.write_work_package("WP-0001", owner="后端", adrs=adrs)
        self.write_state(["WP-0001"], confirmed=adrs)
        self.sync_decisions()

        with self.assertRaisesRegex(MODULE.GovernanceError, "当前前沿最多引用 12 个 ADR"):
            MODULE.verify(self.root)

    def test_decision_index_must_match_adr_metadata(self):
        index = self.root / "project/DECISIONS.md"
        index.write_text(
            index.read_text(encoding="utf-8") + "\n| ADR-9999 | 已接受 | 伪造 | 伪造 |\n",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(MODULE.GovernanceError, "决策索引与 ADR 元数据不一致"):
            MODULE.verify(self.root)

    def test_combined_status_and_date_metadata_is_supported(self):
        path = self.root / "docs/adr/ADR-0001-test.md"
        path.write_text(
            "# ADR-0001：测试决策\n\n"
            "## 状态和日期\n\n"
            "- 状态：已接受\n"
            "- 日期：2026-09-02\n",
            encoding="utf-8",
        )

        self.assertEqual("已接受", MODULE.parse_adr(path).status)

    def test_resume_context_has_twelve_kibibyte_budget(self):
        package = self.root / "project/work-packages/WP-0001.md"
        package.write_text(
            package.read_text(encoding="utf-8") + "\n" + ("过长上下文" * 4096),
            encoding="utf-8",
        )

        with self.assertRaisesRegex(MODULE.GovernanceError, "恢复上下文超过 12 KiB"):
            MODULE.render_status(self.root, resume=True)


if __name__ == "__main__":
    unittest.main()

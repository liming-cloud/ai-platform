import json
import stat
import subprocess
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DELIVERY_DRILL = (
    REPOSITORY_ROOT / "scripts" / "operations" / "demo-delivery-drill.sh"
)
RUNBOOK = REPOSITORY_ROOT / "docs" / "runbooks" / "demo-delivery.md"
REPORT_FILE = (
    REPOSITORY_ROOT
    / "project"
    / "evidence"
    / "artifacts"
    / "WP-0007-delivery-demo.json"
)


class DeliveryOperationsTest(unittest.TestCase):

    def test_delivery_drill_is_executable_and_has_valid_syntax(self) -> None:
        self.assertTrue(DELIVERY_DRILL.is_file(), "缺少空环境交付演练脚本")
        self.assertTrue(
            DELIVERY_DRILL.stat().st_mode & stat.S_IXUSR,
            "空环境交付演练脚本缺少执行权限",
        )
        subprocess.run(["bash", "-n", str(DELIVERY_DRILL)], check=True)

    def test_acceptance_scripts_support_an_isolated_environment(self) -> None:
        expected_variables = {
            "acceptance-knowledge-space.sh": [
                "FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT",
            ],
            "acceptance-index-search.sh": [
                "FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT",
                "FOUNDATION_ACCEPTANCE_QUERY_API_ROOT",
            ],
            "acceptance-model-deployment.sh": [
                "FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT",
            ],
            "acceptance-assistant.sh": [
                "FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT",
            ],
            "acceptance-trusted-query.sh": [
                "FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT",
                "FOUNDATION_ACCEPTANCE_QUERY_API_ROOT",
            ],
            "acceptance-query-web.sh": [
                "FOUNDATION_ACCEPTANCE_QUERY_WEB_ROOT",
            ],
            "acceptance-query-quota.sh": [
                "FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT",
                "FOUNDATION_ACCEPTANCE_QUERY_API_ROOT",
                "FOUNDATION_ACCEPTANCE_QUERY_HEALTH_ROOT",
            ],
            "acceptance-query-operations.sh": [
                "FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT",
            ],
            "evaluation-demo.sh": [
                "FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT",
                "FOUNDATION_ACCEPTANCE_QUERY_API_ROOT",
            ],
        }
        development_directory = REPOSITORY_ROOT / "scripts" / "development"
        for script_name, variables in expected_variables.items():
            content = (development_directory / script_name).read_text(
                encoding="utf-8"
            )
            for variable in variables:
                self.assertIn(variable, content, script_name)

    def test_makefile_and_runbook_expose_the_final_delivery_entrypoint(
        self,
    ) -> None:
        makefile = (REPOSITORY_ROOT / "Makefile").read_text(encoding="utf-8")
        runbook = RUNBOOK.read_text(encoding="utf-8")

        self.assertIn("delivery-operations-test:", makefile)
        self.assertIn("delivery-demo:", makefile)
        self.assertIn("make delivery-demo", runbook)
        self.assertIn("门店演示任务", runbook)
        self.assertIn("校园演示任务", runbook)
        self.assertIn("每日查询上限：100", runbook)
        self.assertIn("本地确定性模型", runbook)
        self.assertIn("退出登录", runbook)

    def test_drill_uses_fresh_resources_and_cleans_them(self) -> None:
        content = DELIVERY_DRILL.read_text(encoding="utf-8")

        self.assertIn("require_absent_recovery_resources", content)
        self.assertIn("initialBusinessFactCount", content)
        self.assertIn("demo-recovery-cleanup.sh", content)
        self.assertIn("trap cleanup EXIT", content)
        self.assertNotIn("source_compose down", content)
        self.assertNotIn("source_compose stop postgres", content)

    def test_committed_delivery_report_meets_the_final_contract(self) -> None:
        report_text = REPORT_FILE.read_text(encoding="utf-8")
        report = json.loads(report_text)

        self.assertEqual(1, report["schemaVersion"])
        self.assertTrue(report["passed"])
        self.assertEqual(0, report["initialBusinessFactCount"])
        self.assertEqual(8, report["serviceCount"])
        self.assertEqual(5, report["applicationEntryCount"])
        self.assertEqual(["门店", "校园"], report["scenarios"])
        self.assertTrue(all(report["tasks"].values()))
        self.assertTrue(report["evaluation"]["passed"])
        self.assertEqual(8, report["evaluation"]["caseCount"])
        self.assertEqual(
            0,
            report["evaluation"]["metrics"]["crossTenantLeakCount"],
        )
        self.assertEqual(2, report["facts"]["tenantCount"])
        self.assertGreaterEqual(report["facts"]["knowledgeObjectCount"], 2)
        self.assertGreater(report["facts"]["citationCount"], 0)
        self.assertEqual(100, report["demoLimits"]["dailyQueryLimit"])
        self.assertEqual(4, report["demoLimits"]["concurrentQueryLimit"])
        self.assertFalse(report["demoLimits"]["publicProductionUse"])
        self.assertEqual(8, len(report["images"]))
        self.assertTrue(report["sourceEnvironmentHealthy"])
        self.assertTrue(report["deliveryResourcesRemoved"])
        self.assertFalse(report["secretsIncluded"])
        self.assertTrue(report["humanMilestoneApprovalRequired"])

        for forbidden in [
            "Demo@2026",
            "local-demo-",
            "tokenValue",
            "object_key",
            "tenant_id",
            "secret://",
        ]:
            self.assertNotIn(forbidden, report_text)


if __name__ == "__main__":
    unittest.main()

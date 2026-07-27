import stat
import subprocess
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
OPERATIONS_DIRECTORY = REPOSITORY_ROOT / "scripts" / "operations"


class CapacityResilienceOperationsTest(unittest.TestCase):

    def test_required_scripts_have_valid_syntax(self) -> None:
        expected_shell_scripts = [
            "demo-capacity-resilience-drill.sh",
            "demo-resilience-verify.sh",
        ]
        for script_name in expected_shell_scripts:
            script = OPERATIONS_DIRECTORY / script_name
            self.assertTrue(script.is_file(), f"缺少运维脚本：{script_name}")
            self.assertTrue(
                script.stat().st_mode & stat.S_IXUSR,
                f"运维脚本缺少执行权限：{script_name}",
            )
            subprocess.run(["bash", "-n", str(script)], check=True)

        capacity_program = OPERATIONS_DIRECTORY / "query_capacity.py"
        self.assertTrue(capacity_program.is_file(), "缺少端到端容量程序")
        self.assertTrue(
            capacity_program.stat().st_mode & stat.S_IXUSR,
            "端到端容量程序缺少执行权限",
        )
        subprocess.run(
            ["python3", "-m", "py_compile", str(capacity_program)],
            check=True,
        )

    def test_makefile_and_runbook_expose_governed_entrypoints(self) -> None:
        makefile = (REPOSITORY_ROOT / "Makefile").read_text(encoding="utf-8")
        runbook = (
            REPOSITORY_ROOT
            / "docs"
            / "runbooks"
            / "demo-capacity-resilience.md"
        ).read_text(encoding="utf-8")

        self.assertIn("capacity-resilience-operations-test:", makefile)
        self.assertIn("capacity-resilience-demo:", makefile)
        self.assertIn("make capacity-resilience-demo", runbook)
        self.assertIn("每租户并发数：4", runbook)
        self.assertIn("错误率：0", runbook)
        self.assertIn("隔离恢复环境", runbook)

    def test_drill_is_isolated_and_always_registers_cleanup(self) -> None:
        drill = (
            OPERATIONS_DIRECTORY / "demo-capacity-resilience-drill.sh"
        ).read_text(encoding="utf-8")

        self.assertIn("require_recovery_name", drill)
        self.assertIn("demo-backup.sh", drill)
        self.assertIn("demo-restore.sh", drill)
        self.assertIn("demo-recovery-cleanup.sh", drill)
        self.assertIn("trap cleanup EXIT", drill)
        self.assertNotIn("source_compose stop postgres", drill)
        self.assertNotIn("source_compose stop minio", drill)


if __name__ == "__main__":
    unittest.main()

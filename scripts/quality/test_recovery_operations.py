import json
import os
import re
import subprocess
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILE = REPOSITORY_ROOT / "deploy" / "compose" / "compose.yml"
ENV_FILE = REPOSITORY_ROOT / "deploy" / "compose" / ".env.example"
OPERATIONS_DIRECTORY = REPOSITORY_ROOT / "scripts" / "operations"


class RecoveryOperationsTest(unittest.TestCase):

    def compose_config(self, overrides: dict[str, str] | None = None) -> dict:
        environment = os.environ.copy()
        environment.update(overrides or {})
        completed = subprocess.run(
            [
                "docker",
                "compose",
                "--env-file",
                str(ENV_FILE),
                "-f",
                str(COMPOSE_FILE),
                "config",
                "--format",
                "json",
            ],
            cwd=REPOSITORY_ROOT,
            env=environment,
            check=True,
            capture_output=True,
            text=True,
        )
        return json.loads(completed.stdout)

    def test_default_compose_resource_names_and_ports_remain_stable(self) -> None:
        configuration = self.compose_config()

        self.assertEqual("trusted-rag-foundation-v1", configuration["name"])
        self.assertEqual(
            "trusted-rag-foundation-v1-postgres",
            configuration["volumes"]["foundation_v1_postgres"]["name"],
        )
        self.assertEqual(
            "trusted-rag-foundation-v1",
            configuration["networks"]["foundation-internal"]["name"],
        )
        self.assertEqual(
            "18080",
            str(configuration["services"]["control-plane"]["ports"][0]["published"]),
        )
        self.assertEqual(
            "13001",
            str(configuration["services"]["query-web"]["ports"][0]["published"]),
        )

    def test_compose_supports_a_fully_isolated_recovery_environment(self) -> None:
        recovery_name = "trusted-rag-foundation-v1-recovery-test"
        configuration = self.compose_config(
            {
                "FOUNDATION_COMPOSE_PROJECT_NAME": recovery_name,
                "FOUNDATION_POSTGRES_VOLUME_NAME": f"{recovery_name}-postgres",
                "FOUNDATION_REDIS_VOLUME_NAME": f"{recovery_name}-redis",
                "FOUNDATION_MINIO_VOLUME_NAME": f"{recovery_name}-minio",
                "FOUNDATION_NETWORK_NAME": recovery_name,
                "FOUNDATION_CONTROL_PLANE_PORT": "28080",
                "FOUNDATION_QUERY_RUNTIME_PORT": "28081",
                "FOUNDATION_INGESTION_WORKER_PORT": "28082",
                "FOUNDATION_CONSOLE_WEB_PORT": "23000",
                "FOUNDATION_QUERY_WEB_PORT": "23001",
            }
        )

        self.assertEqual(recovery_name, configuration["name"])
        self.assertEqual(
            f"{recovery_name}-postgres",
            configuration["volumes"]["foundation_v1_postgres"]["name"],
        )
        self.assertEqual(
            f"{recovery_name}-redis",
            configuration["volumes"]["foundation_v1_redis"]["name"],
        )
        self.assertEqual(
            f"{recovery_name}-minio",
            configuration["volumes"]["foundation_v1_minio"]["name"],
        )
        self.assertEqual(
            recovery_name,
            configuration["networks"]["foundation-internal"]["name"],
        )
        self.assertEqual(
            "28080",
            str(configuration["services"]["control-plane"]["ports"][0]["published"]),
        )
        self.assertEqual(
            "23001",
            str(configuration["services"]["query-web"]["ports"][0]["published"]),
        )

    def test_recovery_scripts_have_valid_shell_syntax(self) -> None:
        expected_scripts = [
            "recovery-common.sh",
            "demo-backup.sh",
            "demo-restore.sh",
            "demo-recovery-verify.sh",
            "demo-recovery-cleanup.sh",
            "demo-recovery-drill.sh",
        ]
        for script_name in expected_scripts:
            script = OPERATIONS_DIRECTORY / script_name
            self.assertTrue(script.is_file(), f"缺少运维脚本：{script_name}")
            subprocess.run(["bash", "-n", str(script)], check=True)

    def test_makefile_and_runbook_expose_the_governed_recovery_entrypoint(
        self,
    ) -> None:
        makefile = (REPOSITORY_ROOT / "Makefile").read_text(encoding="utf-8")
        runbook = (
            REPOSITORY_ROOT / "docs" / "runbooks" / "demo-backup-recovery.md"
        ).read_text(encoding="utf-8")

        self.assertIn("recovery-operations-test:", makefile)
        self.assertIn("recovery-demo:", makefile)
        self.assertIn("make recovery-demo", runbook)
        self.assertIn("Redis 键数为零", runbook)
        self.assertIn("不得提交 Git", runbook)
        self.assertIn("不超过 60 分钟", runbook)

    def test_minio_container_steps_do_not_depend_on_awk(self) -> None:
        for script_name in [
            "demo-backup.sh",
            "demo-restore.sh",
            "demo-recovery-verify.sh",
        ]:
            content = (OPERATIONS_DIRECTORY / script_name).read_text(
                encoding="utf-8"
            )
            self.assertIsNone(
                re.search(r"sha256sum[^\n]*\|\s*awk", content),
                script_name,
            )
            self.assertNotIn("grep -F", content, script_name)

    def test_recovery_name_guard_rejects_source_and_uncontrolled_names(self) -> None:
        common = OPERATIONS_DIRECTORY / "recovery-common.sh"
        command = f'source "{common}"; require_recovery_name "$1"'

        for invalid_name in [
            "trusted-rag-foundation-v1",
            "recovery-test",
            "trusted-rag-foundation-v1-recovery-",
            "trusted-rag-foundation-v1-recovery-UPPER",
        ]:
            completed = subprocess.run(
                ["bash", "-c", command, "test", invalid_name],
                cwd=REPOSITORY_ROOT,
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(0, completed.returncode, invalid_name)

        subprocess.run(
            [
                "bash",
                "-c",
                command,
                "test",
                "trusted-rag-foundation-v1-recovery-contract",
            ],
            cwd=REPOSITORY_ROOT,
            check=True,
        )

    def test_backup_directory_guard_requires_an_absolute_empty_directory(self) -> None:
        common = OPERATIONS_DIRECTORY / "recovery-common.sh"
        command = f'source "{common}"; require_empty_backup_directory "$1"'

        completed = subprocess.run(
            ["bash", "-c", command, "test", "relative-backup"],
            cwd=REPOSITORY_ROOT,
            capture_output=True,
            text=True,
        )
        self.assertNotEqual(0, completed.returncode)

        with tempfile.TemporaryDirectory() as temporary_directory:
            subprocess.run(
                ["bash", "-c", command, "test", temporary_directory],
                cwd=REPOSITORY_ROOT,
                check=True,
            )
            Path(temporary_directory, "existing.txt").write_text(
                "已有内容",
                encoding="utf-8",
            )
            completed = subprocess.run(
                ["bash", "-c", command, "test", temporary_directory],
                cwd=REPOSITORY_ROOT,
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(0, completed.returncode)


if __name__ == "__main__":
    unittest.main()

import re
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEPLOY = ROOT / "deploy"


class DeliveryCleanupTest(unittest.TestCase):
    def test_process_assets_are_removed(self) -> None:
        for path in (
            ROOT / "artifacts",
            ROOT / "docs" / "00-index",
            DEPLOY / "tests",
            DEPLOY / "security",
            DEPLOY / "evidence",
            DEPLOY / "pgvector",
            DEPLOY / "object-storage",
        ):
            self.assertFalse(path.exists(), str(path.relative_to(ROOT)))

        self.assertTrue((ROOT / "docs" / "PROJECT_DELIVERY.md").is_file())
        self.assertEqual(
            ["broker.conf"],
            sorted(path.name for path in (DEPLOY / "rocketmq").iterdir()),
        )

    def test_open_source_images_use_version_tags_without_local_builds(self) -> None:
        compose = (DEPLOY / "docker-compose.yml").read_text(encoding="utf-8")
        self.assertNotIn("@sha256:", compose)
        for image in (
            "pgvector/pgvector:0.8.2-pg16-bookworm",
            "public.ecr.aws/docker/library/redis:8.8.0-alpine3.23",
            "chrislusf/seaweedfs:4.29",
            "apache/rocketmq:5.5.0",
        ):
            self.assertIn(f"image: {image}", compose)
        self.assertEqual(2, len(re.findall(r"(?m)^    build:", compose)))

    def test_base_seed_contains_no_tenant_or_business_data(self) -> None:
        seed = DEPLOY / "postgres" / "init" / "02-seed-base.sql"
        self.assertTrue(seed.is_file())
        self.assertFalse((seed.parent / "02-seed-local.sql").exists())

        sql = seed.read_text(encoding="utf-8")
        inserted_tables = set(
            re.findall(r"(?im)^INSERT\s+INTO\s+([a-z0-9_]+)", sql)
        )
        self.assertEqual(
            {
                "iam_menu",
                "iam_menu_feature",
                "iam_permission",
                "iam_role",
                "iam_role_menu",
                "iam_role_permission",
                "iam_tenant",
                "iam_tenant_feature",
                "iam_user",
                "iam_user_role",
            },
            inserted_tables,
        )
        self.assertIn("VALUES (2001, 'platform', '平台管理租户'", sql)
        self.assertIn("'admin'", sql)
        self.assertIn("'platform-admin'", sql)
        self.assertNotIn("'tenant-admin@local.aip'", sql)
        self.assertNotIn("'user@local.aip'", sql)
        self.assertNotIn("'business-user',\n  '普通用户'", sql)
        for business_table in (
            "mdl_provider",
            "mdl_config",
            "kb_knowledge_base",
            "app_application",
        ):
            self.assertNotRegex(sql, rf"(?im)^\s*(INSERT\s+INTO|UPDATE)\s+{business_table}\b")

    def test_database_initializer_has_no_migration_chain(self) -> None:
        initializer = DEPLOY / "postgres" / "initialize.sh"
        self.assertTrue(initializer.is_file())
        self.assertFalse((DEPLOY / "postgres" / "migrate.sh").exists())
        self.assertFalse((DEPLOY / "postgres" / "migrations").exists())
        self.assertTrue((DEPLOY / "postgres" / "init" / "01-schema.sql").is_file())

        initializer_text = initializer.read_text(encoding="utf-8")
        self.assertIn("printf '\\\\set bootstrap_credentials_only false", initializer_text)
        self.assertNotRegex(
            initializer_text,
            r"(?i)checksum|aip_schema_migration|apply_migration|/migrations",
        )

        compose = (DEPLOY / "docker-compose.yml").read_text(encoding="utf-8")
        self.assertIn("database-initialize:", compose)
        self.assertNotIn("postgres-migrate", compose)
        self.assertNotIn("/migrations", compose)

        makefile = (ROOT / "Makefile").read_text(encoding="utf-8")
        self.assertIn("deploy-initialize", makefile)
        self.assertNotIn("deploy-migrate", makefile)

    def test_environment_upgrade_removes_legacy_acceptance_credentials(self) -> None:
        with tempfile.TemporaryDirectory(prefix="aip-delivery-env-") as directory:
            environment = Path(directory) / ".env"
            subprocess.run(
                ["sh", str(DEPLOY / "bin" / "init-env.sh"), str(environment)],
                check=True,
                capture_output=True,
                text=True,
            )
            with environment.open("a", encoding="utf-8") as handle:
                handle.write("AIP_LOCAL_ACCEPTANCE_PASSWORD=legacy-value\\n")
                handle.write("AIP_LOCAL_ACCEPTANCE_PASSWORD_HASH=legacy-hash\\n")
            subprocess.run(
                ["sh", str(DEPLOY / "bin" / "upgrade-env.sh"), str(environment)],
                check=True,
                capture_output=True,
                text=True,
            )
            names = {
                line.split("=", 1)[0]
                for line in environment.read_text(encoding="utf-8").splitlines()
                if "=" in line
            }
            self.assertNotIn("AIP_LOCAL_ACCEPTANCE_PASSWORD", names)
            self.assertNotIn("AIP_LOCAL_ACCEPTANCE_PASSWORD_HASH", names)


if __name__ == "__main__":
    unittest.main()

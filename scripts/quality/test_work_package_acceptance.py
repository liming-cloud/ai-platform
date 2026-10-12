"""工作包验收入口必须明确独立范围，并在缺失依赖时失败关闭。"""

from pathlib import Path
import os
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
ENTRY = ROOT / "scripts/development/acceptance-work-package.sh"


class WorkPackageAcceptanceTest(unittest.TestCase):
    def invoke(self, wp="", slice_name="", **variables):
        environment = {key: value for key, value in os.environ.items()
                       if not key.startswith("FOUNDATION_ACCEPTANCE_") and key != "SETUP"}
        environment.update(variables)
        return subprocess.run(["bash", str(ENTRY), wp, slice_name], cwd=ROOT,
                              env=environment, capture_output=True, text=True, timeout=10)

    def test_member_account_is_named_slice_and_missing_credentials_fail_closed(self):
        result = self.invoke("WP-0018", "account")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_member_whole_package_requires_credentials(self):
        result = self.invoke("WP-0018")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_member_whole_package_dispatches_only_read_only_integration(self):
        with tempfile.TemporaryDirectory() as directory:
            node = Path(directory) / "node"
            node.write_text('#!/bin/sh\nprintf "%s\\n" "$1"\n')
            node.chmod(0o700)
            result = self.invoke("WP-0018", PATH=f"{directory}:{os.environ['PATH']}",
                                 FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE="/private/tmp/not-used.json")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(str(ROOT / "scripts/development/acceptance-member-package.cjs"), result.stdout.strip())

    def test_member_account_rejects_unknown_slice_and_tenant_setup(self):
        result = self.invoke("WP-0018", "all")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("不支持的 WP-0018 验收范围", result.stderr)
        result = self.invoke("WP-0018", "account", SETUP="tenant")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("不接受外部 SETUP", result.stderr)

    def test_member_whole_package_rejects_object_preparation(self):
        result = self.invoke("WP-0018", SETUP="tenant",
                             FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE="/private/tmp/not-used.json")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("不接受外部 SETUP", result.stderr)

    def test_member_assistants_is_named_slice_and_missing_credentials_fail_closed(self):
        result = self.invoke("WP-0018", "assistants")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_tenant_roles_is_named_read_only_slice(self):
        result = self.invoke("WP-0018", "roles")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_tenant_roles_rejects_object_preparation(self):
        result = self.invoke("WP-0018", "roles", SETUP="tenant",
                             FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE="/private/tmp/not-used.json")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("不接受外部 SETUP", result.stderr)

    def test_member_features_is_named_slice_and_missing_credentials_fail_closed(self):
        result = self.invoke("WP-0018", "features")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_unknown_work_package_is_rejected(self):
        result = self.invoke("WP-9999", "login")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("不支持的工作包", result.stderr)

    def test_platform_connection_requires_explicit_slice(self):
        for slice_name in ("", "all", "platform-supply", "routing"):
            result = self.invoke("WP-0019", slice_name)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("必须明确 SLICE=platform-connection", result.stderr)

    def test_platform_connection_requires_credentials(self):
        result = self.invoke("WP-0019", "platform-connection")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_platform_connection_rejects_setup(self):
        result = self.invoke("WP-0019", "platform-connection", SETUP="tenant")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("不接受外部 SETUP", result.stderr)

    def test_platform_connection_dispatches_scoped_entry(self):
        with tempfile.TemporaryDirectory() as directory:
            node = Path(directory) / "node"
            node.write_text('#!/bin/sh\nprintf "%s\\n" "$1"\n')
            node.chmod(0o700)
            result = self.invoke("WP-0019", "platform-connection",
                                 PATH=f"{directory}:{os.environ['PATH']}",
                                 FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE="/private/tmp/not-used.json")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(str(ROOT / "scripts/development/acceptance-platform-connection.cjs"),
                         result.stdout.strip())

    def test_whole_package_cannot_be_claimed_by_login_slice(self):
        result = self.invoke("WP-0017")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须明确 SLICE=login", result.stderr)

    def test_unknown_slice_is_rejected(self):
        result = self.invoke("WP-0017", "all")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须明确 SLICE=login", result.stderr)

    def test_missing_credentials_are_not_skipped(self):
        result = self.invoke("WP-0017", "login")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_onboarding_is_a_named_slice_not_whole_package_success(self):
        result = self.invoke("WP-0017", "onboarding")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_admins_is_a_named_slice_not_whole_package_success(self):
        result = self.invoke("WP-0017", "admins")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_security_is_a_named_slice_not_whole_package_success(self):
        result = self.invoke("WP-0017", "security")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_accounts_is_a_named_slice_not_whole_package_success(self):
        result = self.invoke("WP-0017", "accounts")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_overview_is_a_named_read_only_slice(self):
        result = self.invoke("WP-0017", "overview")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_overview_rejects_object_preparation(self):
        result = self.invoke("WP-0017", "overview", SETUP="tenant",
                             FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE="/private/tmp/not-used.json")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("概览切片只读，不提供业务对象准备", result.stderr)

    def test_tenants_is_a_named_read_only_slice(self):
        result = self.invoke("WP-0017", "tenants")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_tenants_rejects_object_preparation(self):
        result = self.invoke("WP-0017", "tenants", SETUP="tenant",
                             FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE="/private/tmp/not-used.json")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("目录切片只读，不提供业务对象准备", result.stderr)

    def test_login_entries_is_a_named_navigation_slice(self):
        result = self.invoke("WP-0017", "login-entries")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_login_entries_rejects_object_preparation(self):
        result = self.invoke("WP-0017", "login-entries", SETUP="tenant",
                             FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE="/private/tmp/not-used.json")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("登录导航切片只读，不提供业务对象准备", result.stderr)

    def test_setup_requires_explicit_supported_value(self):
        result = self.invoke("WP-0017", "login", SETUP="reset")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SETUP 只允许 tenant", result.stderr)

    def test_make_target_forwards_explicit_scope(self):
        makefile = (ROOT / "Makefile").read_text()
        self.assertIn("acceptance-work-package:", makefile)
        self.assertIn('acceptance-work-package.sh "$(WP)" "$(SLICE)"', makefile)


if __name__ == "__main__":
    unittest.main()

"""WP-0017 登录切片验收入口必须明确范围并在缺失依赖时失败关闭。"""

from pathlib import Path
import os
import subprocess
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

    def test_member_account_rejects_whole_package_and_tenant_setup(self):
        for slice_name in ("", "all", "assistants"):
            result = self.invoke("WP-0018", slice_name)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("必须明确 SLICE=account", result.stderr)
        result = self.invoke("WP-0018", "account", SETUP="tenant")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("成员账户切片不创建或重置租户", result.stderr)

    def test_member_features_is_named_slice_and_missing_credentials_fail_closed(self):
        result = self.invoke("WP-0018", "features")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("必须提供 0600 验收凭据文件", result.stderr)

    def test_unknown_work_package_is_rejected(self):
        result = self.invoke("WP-9999", "login")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("不支持的工作包", result.stderr)

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

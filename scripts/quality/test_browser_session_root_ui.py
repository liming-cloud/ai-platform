"""ADR-0048 统一浏览器登录入口前端门禁。"""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
CONSOLE = ROOT / "frontend/apps/console-web/src"
QUERY = ROOT / "frontend/apps/query-web/src"


class BrowserSessionRootUiTest(unittest.TestCase):
    """控制台凭据表单只能存在于根级浏览器会话入口。"""

    def test_console_uses_a_root_browser_session_gate(self):
        root = (CONSOLE / "ConsoleApp.tsx").read_text()
        gate = (CONSOLE / "session/BrowserSessionGate.tsx").read_text()

        self.assertIn("<BrowserSessionGate", root)
        self.assertIn("restoreSession", gate)
        self.assertEqual(gate.count("<form"), 1)

    def test_console_workspaces_do_not_render_login_forms(self):
        violations = []
        for source in CONSOLE.rglob("*.tsx"):
            if source == CONSOLE / "session/BrowserSessionGate.tsx":
                continue
            if "LoginForm" in source.read_text():
                violations.append(source.relative_to(CONSOLE).as_posix())

        self.assertEqual(violations, [])

    def test_query_workspace_restores_or_returns_to_console_root(self):
        root = (QUERY / "QueryApp.tsx").read_text()
        workspace = (QUERY / "features/query/QueryWorkspace.tsx").read_text()

        self.assertIn("restoreSession", root)
        self.assertIn("/?next=%2Fquery%2F", root)
        self.assertNotIn("LoginView", workspace)


if __name__ == "__main__":
    unittest.main()

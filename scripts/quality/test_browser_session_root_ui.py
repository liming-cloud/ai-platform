"""ADR-0048 统一浏览器登录入口前端门禁。"""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
CONSOLE = ROOT / "frontend/apps/console-web/src"


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


if __name__ == "__main__":
    unittest.main()

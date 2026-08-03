"""ADR-0048 同源浏览器会话部署门禁。"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class BrowserSessionDeploymentTest(unittest.TestCase):
    """公开入口必须使两个前端应用共享一个浏览器 origin。"""

    def test_query_web_only_through_console_gateway_is_public(self) -> None:
        compose = (ROOT / "deploy/compose/compose.yml").read_text(encoding="utf-8")
        query_service = re.search(
            r"^  query-web:\n(?P<body>.*?)(?=^  [a-z0-9-]+:|\Z)",
            compose,
            re.MULTILINE | re.DOTALL,
        )
        self.assertIsNotNone(query_service)
        self.assertNotIn("    ports:\n", query_service.group("body"))

    def test_console_gateway_routes_query_app_and_query_runtime(self) -> None:
        nginx = (ROOT / "deploy/config/nginx-console.conf").read_text(encoding="utf-8")
        self.assertIn("location ^~ /query/", nginx)
        self.assertIn("http://query-web:8080", nginx)
        self.assertIn("location ^~ /api/v1/query-assistants", nginx)
        self.assertIn("location ^~ /api/v1/query-runs", nginx)
        self.assertIn("http://query-runtime:8081", nginx)

    def test_query_web_builds_for_gateway_subpath(self) -> None:
        vite = (ROOT / "frontend/apps/query-web/vite.config.ts").read_text(encoding="utf-8")
        self.assertIn('base: "/query/"', vite)


if __name__ == "__main__":
    unittest.main()

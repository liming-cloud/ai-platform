"""ADR-0048 同源浏览器会话部署门禁。"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class BrowserSessionDeploymentTest(unittest.TestCase):
    """公开入口必须使两个前端应用共享一个浏览器 origin。"""

    def test_console_is_the_only_personnel_browser_spa_gateway(self) -> None:
        compose = (ROOT / "deploy/compose/compose.yml").read_text(encoding="utf-8")
        self.assertNotIn("  query-web:\n", compose)
        console_service = re.search(
            r"^  console-web:\n(?P<body>.*?)(?=^  [a-z0-9-]+:|\Z)",
            compose,
            re.MULTILINE | re.DOTALL,
        )
        self.assertIsNotNone(console_service)
        self.assertNotIn("query-web", console_service.group("body"))

    def test_console_gateway_only_proxies_query_runtime_api(self) -> None:
        nginx = (ROOT / "deploy/config/nginx-console.conf").read_text(encoding="utf-8")
        https_nginx = (ROOT / "deploy/config/nginx-console-https.conf").read_text(encoding="utf-8")
        for configuration in (nginx, https_nginx):
            self.assertNotIn("location ^~ /query/", configuration)
            self.assertNotIn("query-web", configuration)
        self.assertIn("location ^~ /api/v1/query-assistants", nginx)
        self.assertIn("location ^~ /api/v1/query-runs", nginx)
        self.assertIn("http://query-runtime:8081", nginx)

    def test_console_gateway_preserves_public_host_for_csrf_origin_validation(self) -> None:
        nginx = (ROOT / "deploy/config/nginx-console.conf").read_text(encoding="utf-8")
        control_plane = re.search(
            r"    location /api/ \{(?P<body>.*?)^    \}",
            nginx,
            re.MULTILINE | re.DOTALL,
        )
        self.assertIsNotNone(control_plane)
        self.assertIn("proxy_set_header Host $http_host;", control_plane.group("body"))

    def test_query_web_source_is_retired(self) -> None:
        self.assertFalse((ROOT / "frontend/apps/query-web/package.json").exists())

    def test_frontend_html_entries_must_revalidate_after_image_replacement(self) -> None:
        for name in ("nginx-console.conf", "nginx-console-https.conf"):
            nginx = (ROOT / "deploy/config" / name).read_text(encoding="utf-8")
            self.assertRegex(
                nginx,
                r'location = /index\.html \{(?s:.*?)Cache-Control "no-cache, no-store, must-revalidate"',
                msg=f"{name} 必须避免旧入口脚本引用已替换的哈希资源",
            )
            self.assertRegex(
                nginx,
                r'location = / \{(?s:.*?)Cache-Control "no-cache, no-store, must-revalidate"',
                msg=f"{name} 的根路径必须避免由目录索引返回旧入口脚本",
            )

    def test_local_browser_acceptance_overlay_uses_http_and_expirable_session(self) -> None:
        overlay = ROOT / "deploy/compose/compose.browser-acceptance.yml"
        self.assertTrue(overlay.is_file(), "缺少本地 HTTP 浏览器验收覆盖层")
        compose = (ROOT / "deploy/compose/compose.yml").read_text(encoding="utf-8")
        self.assertIn("FOUNDATION_SECURITY_SESSION_TIMEOUT", compose)
        text = overlay.read_text(encoding="utf-8")
        self.assertIn("127.0.0.1:${FOUNDATION_CONSOLE_ACCEPTANCE_PORT:-13443}:8080", text)
        self.assertNotIn("nginx-console-https.conf", text)
        self.assertNotIn("localhost.crt", text)
        self.assertNotIn("localhost.key", text)


if __name__ == "__main__":
    unittest.main()

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("frontend_product_guard.py")
SPEC = importlib.util.spec_from_file_location("frontend_product_guard", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class FrontendProductGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)

    def tearDown(self):
        self.temp_dir.cleanup()

    def write_workspace(self, name, source):
        path = self.root / "frontend/apps/console-web/src/features" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")

    def test_rejects_browser_accumulated_pagination(self):
        self.write_workspace(
            "DemoWorkspace.tsx",
            "async function loadMore() { setItems((items) => [...items, ...page.items]); }",
        )

        errors = MODULE.scan(self.root)

        self.assertTrue(any("加载更多" in error for error in errors))

    def test_rejects_appending_the_next_server_page_to_browser_state(self):
        self.write_workspace(
            "DemoWorkspace.tsx",
            "setItems((items) => [...items, ...page.items]);",
        )

        errors = MODULE.scan(self.root)

        self.assertTrue(any("追加 page.items" in error for error in errors))

    def test_rejects_nested_main_dialog(self):
        self.write_workspace(
            "DemoWorkspace.tsx",
            "<Dialog><DialogSurface><Dialog><DialogSurface /></Dialog></DialogSurface></Dialog>",
        )

        errors = MODULE.scan(self.root)

        self.assertTrue(any("嵌套 Dialog" in error for error in errors))

    def test_allows_sibling_dialog_tasks_and_server_page_replacement(self):
        self.write_workspace(
            "DemoWorkspace.tsx",
            "<Dialog><DialogSurface /></Dialog><Dialog><DialogSurface /></Dialog>"
            "async function loadPage() { setItems(page.items); }",
        )

        self.assertEqual([], MODULE.scan(self.root))


if __name__ == "__main__":
    unittest.main()

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

    def test_rejects_internal_identifiers_in_editable_control_prompts(self):
        self.write_workspace(
            "DemoWorkspace.tsx",
            '<Field label="SubjectId"><Input placeholder="请输入 modelId" /></Field>',
        )

        errors = MODULE.scan(self.root)

        self.assertTrue(any("内部标识输入" in error for error in errors))

    def test_rejects_unlabelled_internal_identifier_editors(self):
        self.write_workspace(
            "DemoWorkspace.tsx",
            '<Input name="tenantId" value={tenantId} />',
        )

        errors = MODULE.scan(self.root)

        self.assertTrue(any("内部标识编辑控件" in error for error in errors))

    def test_rejects_internal_identifiers_in_visible_response_content(self):
        self.write_workspace(
            "DemoWorkspace.tsx",
            "<table><thead><tr><th>助手版本</th></tr></thead>"
            "<tbody><tr><td title={record.assistantVersionId}>"
            "{record.assistantVersionId}</td></tr></tbody></table>",
        )

        errors = MODULE.scan(self.root)

        self.assertTrue(any("内部响应信息" in error for error in errors))

    def test_allows_sibling_dialog_tasks_and_server_page_replacement(self):
        self.write_workspace(
            "DemoWorkspace.tsx",
            "<Dialog><DialogSurface /></Dialog><Dialog><DialogSurface /></Dialog>"
            "async function loadPage() { setItems(page.items); }",
        )

        self.assertEqual([], MODULE.scan(self.root))


if __name__ == "__main__":
    unittest.main()

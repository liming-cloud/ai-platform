import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("backend_architecture_guard.py")
SPEC = importlib.util.spec_from_file_location("backend_architecture_guard", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class BackendArchitectureGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)

    def tearDown(self):
        self.temp_dir.cleanup()

    def write_java(self, relative, content):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def test_new_sql_annotation_is_rejected(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/DemoMapper.java",
            'import org.apache.ibatis.annotations.Select;\n@Select("select 1")\n',
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("SQL 注解" in error for error in errors))

    def test_new_uuid_use_is_rejected(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/DemoId.java",
            "import java.util.UUID;\n",
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("UUID" in error for error in errors))

    def test_runtime_business_class_is_rejected(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/runtimes/demo/src/main/java/demo/DemoController.java",
            "final class DemoController {}\n",
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("运行模块" in error for error in errors))

    def test_sa_token_outside_platform_adapter_is_rejected(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/DemoSession.java",
            "import cn.dev33.satoken.stp.StpLogic;\n",
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("Sa-Token" in error for error in errors))

    def test_new_flat_package_is_rejected(self):
        baseline = MODULE.scan(self.root)
        for index in range(13):
            self.write_java(
                f"backend/contexts/demo/src/main/java/demo/flat/C{index}.java",
                f"package demo.flat; final class C{index} {{}}\n",
            )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("包规模" in error for error in errors))

    def test_context_infrastructure_directory_is_allowed(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/infrastructure/Thing.java",
            "final class Thing {}\n",
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertFalse(any("基础设施目录" in error for error in errors))

    def test_new_context_api_directory_is_rejected(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/api/DemoUseCase.java",
            "interface DemoUseCase {}\n",
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("上下文 api 目录" in error for error in errors))

    def test_new_context_adapter_directory_is_rejected(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/adapter/DemoAdapter.java",
            "final class DemoAdapter {}\n",
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("上下文 adapter 目录" in error for error in errors))

    def test_new_runtime_file_is_rejected(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/runtimes/demo/src/main/java/demo/DemoApplication.java",
            "final class DemoApplication {}\n",
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("运行模块目录" in error for error in errors))

    def test_new_cursor_list_contract_is_rejected(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/application/list/DemoPage.java",
            "record DemoPage(String nextCursor) {}\n",
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("游标列表分页" in error for error in errors))

    def test_new_manual_list_limit_is_rejected(self):
        baseline = MODULE.scan(self.root)
        mapper = self.root / (
            "backend/contexts/demo/src/main/resources/mapper/demo/DemoMapper.xml"
        )
        mapper.parent.mkdir(parents=True, exist_ok=True)
        mapper.write_text(
            """
            <mapper namespace="demo.DemoMapper">
              <select id="findPage">SELECT * FROM demo LIMIT #{limit}</select>
            </mapper>
            """,
            encoding="utf-8",
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("手工列表分页" in error for error in errors))

    def test_removing_existing_debt_is_allowed(self):
        path = self.write_java(
            "backend/contexts/demo/src/main/java/demo/DemoId.java",
            "import java.util.UUID;\n",
        )
        baseline = MODULE.scan(self.root)
        path.unlink()

        self.assertEqual([], MODULE.compare(MODULE.scan(self.root), baseline))


if __name__ == "__main__":
    unittest.main()

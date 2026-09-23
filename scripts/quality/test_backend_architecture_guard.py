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

    def write_allowlist(self, statements):
        path = (
            self.root
            / "scripts/quality/baselines/complex-sql-allowlist.json"
        )
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            MODULE.json.dumps(
                {"schemaVersion": 1, "statements": statements},
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )

    def write_pom(self, relative, content):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def write_migration(self, relative):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("SELECT 1;\n", encoding="utf-8")
        return path

    def test_new_sql_annotation_is_rejected(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/DemoMapper.java",
            'import org.apache.ibatis.annotations.Select;\n@Select("select 1")\n',
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("SQL 注解" in error for error in errors))

    def test_new_java_inline_sql_is_rejected(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/DemoDirectory.java",
            """
            import org.springframework.jdbc.core.simple.JdbcClient;

            final class DemoDirectory {
                long count(JdbcClient jdbc) {
                    return jdbc.sql("SELECT COUNT(*) FROM demo.item")
                            .query(Long.class)
                            .single();
                }
            }
            """,
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("Java 内联 SQL" in error for error in errors))

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

    def test_unregistered_mapper_statement_is_rejected(self):
        mapper = self.root / (
            "backend/contexts/demo/src/main/resources/mapper/demo/DemoMapper.xml"
        )
        mapper.parent.mkdir(parents=True, exist_ok=True)
        mapper.write_text(
            '<mapper namespace="demo.DemoMapper">'
            '<select id="findOne">SELECT 1</select></mapper>',
            encoding="utf-8",
        )

        errors = MODULE.compare(MODULE.scan(self.root), MODULE.scan(Path("/不存在")))

        self.assertTrue(any("XML SQL 未登记" in error for error in errors))

    def test_stale_complex_sql_registration_is_rejected(self):
        self.write_allowlist(
            [
                {
                    "path": (
                        "backend/contexts/demo/src/main/resources/"
                        "mapper/demo/DemoMapper.xml"
                    ),
                    "statementId": "missing",
                    "category": "ROW_LOCK",
                    "reason": "登记项已经没有对应的 XML 语句。",
                }
            ]
        )

        errors = MODULE.compare(MODULE.scan(self.root), MODULE.scan(Path("/不存在")))

        self.assertTrue(any("复杂 SQL 失效登记" in error for error in errors))

    def test_duplicate_flyway_migration_version_is_rejected_across_contexts(self):
        self.write_migration(
            "backend/contexts/organization-access/src/main/resources/"
            "db/migration/organization-access/V202608100004__organization.sql"
        )
        self.write_migration(
            "backend/contexts/model-runtime/src/main/resources/"
            "db/migration/model-runtime/V202608100004__model.sql"
        )

        errors = MODULE.compare(MODULE.scan(self.root), MODULE.scan(Path("/不存在")))

        self.assertTrue(any("Flyway 迁移版本重复" in error for error in errors))

    def test_event_replay_limit_is_not_treated_as_page_pagination(self):
        mapper_path = (
            "backend/contexts/demo/src/main/resources/mapper/demo/DemoMapper.xml"
        )
        mapper = self.root / mapper_path
        mapper.parent.mkdir(parents=True, exist_ok=True)
        mapper.write_text(
            '<mapper namespace="demo.DemoMapper">'
            '<select id="findAfter">'
            'SELECT * FROM event WHERE sequence > #{after} LIMIT #{limit}'
            '</select></mapper>',
            encoding="utf-8",
        )
        self.write_allowlist(
            [
                {
                    "path": mapper_path,
                    "statementId": "findAfter",
                    "category": "EVENT_REPLAY",
                    "reason": "按单调序号回放事件流，不属于列表分页。",
                }
            ]
        )

        actual = MODULE.scan(self.root)

        self.assertEqual([], actual["complexSqlAllowlistErrors"])
        self.assertEqual(0, actual["manualListLimitCount"])

    def test_invalid_error_code_is_rejected(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/DemoErrorCode.java",
            'enum DemoErrorCode { BAD("DEMO.UNKNOWN.1"); }\n',
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("错误码格式" in error for error in errors))

    def test_duplicate_error_code_is_rejected(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/FirstErrorCode.java",
            'enum FirstErrorCode { BAD("DEMO.REQUEST.0001"); }\n',
        )
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/SecondErrorCode.java",
            'enum SecondErrorCode { BAD("DEMO.REQUEST.0001"); }\n',
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("错误码重复" in error for error in errors))

    def test_removing_existing_debt_is_allowed(self):
        path = self.write_java(
            "backend/contexts/demo/src/main/java/demo/DemoId.java",
            "import java.util.UUID;\n",
        )
        baseline = MODULE.scan(self.root)
        path.unlink()

        self.assertEqual([], MODULE.compare(MODULE.scan(self.root), baseline))

    def test_non_aggregate_entity_is_rejected(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/domain/DemoEntity.java",
            "final class DemoEntity implements Entity<DemoId> {}\n",
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("非聚合根实体" in error for error in errors))

    def test_application_service_constructor_cannot_depend_on_value_object(self):
        baseline = MODULE.scan(self.root)
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/domain/DemoRule.java",
            "record DemoRule() implements ValueObject {}\n",
        )
        self.write_java(
            "backend/contexts/demo/src/main/java/demo/application/DemoService.java",
            "final class DemoService { DemoService(DemoRule rule) {} }\n",
        )

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("应用服务构造器领域状态依赖" in error for error in errors))


    def test_current_backend_dependency_boundary_is_closed(self):
        root = Path(__file__).resolve().parents[2]
        actual = MODULE.scan(root)

        self.assertEqual([], actual["backendDependencyBoundaryViolations"])

    def test_cross_context_domain_import_is_rejected(self):
        self.write_java(
            "backend/contexts/alpha/src/main/java/com/example/alpha/Alpha.java",
            """
            package com.example.alpha;
            import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
            final class Alpha { ModelConfigurationId value; }
            """,
        )
        self.write_java(
            "backend/contexts/model-runtime/src/main/java/com/aiplatform/foundation/modelruntime/domain/configuration/model/ModelConfigurationId.java",
            "package com.aiplatform.foundation.modelruntime.domain.configuration.model; public record ModelConfigurationId(long value) {}\n",
        )

        violations = MODULE.backend_dependency_boundary_violations(self.root)

        self.assertTrue(any("domain" in violation for violation in violations))

    def test_cross_context_facade_import_is_allowed(self):
        self.write_pom(
            "backend/contexts/alpha/pom.xml",
            "<project><artifactId>alpha</artifactId><dependencies><dependency><artifactId>model-runtime</artifactId></dependency></dependencies></project>",
        )
        self.write_pom(
            "backend/contexts/model-runtime/pom.xml",
            "<project><artifactId>model-runtime</artifactId></project>",
        )
        self.write_java(
            "backend/contexts/alpha/src/main/java/com/example/alpha/Alpha.java",
            """
            package com.example.alpha;
            import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
            final class Alpha { UsableModelConfigurationLookup value; }
            """,
        )
        self.write_java(
            "backend/contexts/model-runtime/src/main/java/com/aiplatform/foundation/modelruntime/application/configuration/facade/UsableModelConfigurationLookup.java",
            "package com.aiplatform.foundation.modelruntime.application.configuration.facade; public interface UsableModelConfigurationLookup {}\n",
        )

        violations = MODULE.backend_dependency_boundary_violations(self.root)

        self.assertEqual([], violations)

    def test_cross_context_port_import_is_rejected(self):
        self.write_pom(
            "backend/contexts/alpha/pom.xml",
            "<project><artifactId>alpha</artifactId><dependencies><dependency><artifactId>model-runtime</artifactId></dependency></dependencies></project>",
        )
        self.write_pom(
            "backend/contexts/model-runtime/pom.xml",
            "<project><artifactId>model-runtime</artifactId></project>",
        )
        self.write_java(
            "backend/contexts/alpha/src/main/java/com/example/alpha/Alpha.java",
            "import com.aiplatform.foundation.modelruntime.application.runtime.port.out.ChatModelFactory;",
        )

        violations = MODULE.backend_dependency_boundary_violations(self.root)

        self.assertTrue(any("禁止引用" in violation for violation in violations))

    def test_cross_context_import_without_maven_dependency_is_rejected(self):
        self.write_pom("backend/contexts/alpha/pom.xml", "<project><artifactId>alpha</artifactId></project>")
        self.write_pom("backend/contexts/model-runtime/pom.xml", "<project><artifactId>model-runtime</artifactId></project>")
        self.write_java(
            "backend/contexts/alpha/src/main/java/com/example/alpha/Alpha.java",
            "import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;",
        )

        violations = MODULE.backend_dependency_boundary_violations(self.root)

        self.assertTrue(any("缺少 Maven" in violation for violation in violations))

    def test_extension_infrastructure_import_is_rejected(self):
        self.write_java(
            "backend/extensions/model-providers/provider-demo/src/main/java/com/example/provider/Demo.java",
            """
            package com.example.provider;
            import com.aiplatform.foundation.modelruntime.infrastructure.configuration.ModelRuntimeProperties;
            final class Demo { ModelRuntimeProperties value; }
            """,
        )
        self.write_java(
            "backend/contexts/model-runtime/src/main/java/com/aiplatform/foundation/modelruntime/infrastructure/configuration/ModelRuntimeProperties.java",
            "package com.aiplatform.foundation.modelruntime.infrastructure.configuration; public class ModelRuntimeProperties {}\n",
        )

        violations = MODULE.backend_dependency_boundary_violations(self.root)

        self.assertTrue(any("extension" in violation and "infrastructure" in violation for violation in violations))

    def test_extension_domain_import_is_rejected(self):
        self.write_pom(
            "backend/extensions/model-providers/provider-demo/pom.xml",
            "<project><artifactId>provider-demo</artifactId><dependencies><dependency><artifactId>model-runtime</artifactId></dependency></dependencies></project>",
        )
        self.write_pom(
            "backend/contexts/model-runtime/pom.xml",
            "<project><artifactId>model-runtime</artifactId></project>",
        )
        self.write_java(
            "backend/extensions/model-providers/provider-demo/src/main/java/com/example/provider/Demo.java",
            "import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;",
        )

        violations = MODULE.backend_dependency_boundary_violations(self.root)

        self.assertTrue(any("extension" in violation and "domain" in violation for violation in violations))

    def test_cross_context_auto_configuration_import_is_rejected(self):
        self.write_pom(
            "backend/contexts/alpha/pom.xml",
            "<project><artifactId>alpha</artifactId><dependencies><dependency><artifactId>model-runtime</artifactId></dependency></dependencies></project>",
        )
        self.write_pom(
            "backend/contexts/model-runtime/pom.xml",
            "<project><artifactId>model-runtime</artifactId></project>",
        )
        self.write_java(
            "backend/contexts/alpha/src/main/java/com/example/alpha/Alpha.java",
            "import com.aiplatform.foundation.modelruntime.infrastructure.configuration.ModelRuntimeCoreAutoConfiguration;",
        )

        violations = MODULE.backend_dependency_boundary_violations(self.root)

        self.assertTrue(any("上下文" in violation and "infrastructure" in violation for violation in violations))

    def test_unused_cross_context_maven_dependency_is_rejected(self):
        self.write_pom(
            "backend/contexts/alpha/pom.xml",
            "<project><artifactId>alpha</artifactId><dependencies><dependency><artifactId>model-runtime</artifactId></dependency></dependencies></project>",
        )
        self.write_pom(
            "backend/contexts/model-runtime/pom.xml",
            "<project><artifactId>model-runtime</artifactId></project>",
        )

        violations = MODULE.backend_dependency_boundary_violations(self.root)

        self.assertTrue(any("未使用" in violation for violation in violations))

    def test_cross_context_dependency_cycle_is_rejected(self):
        self.write_pom(
            "backend/contexts/alpha/pom.xml",
            """
            <project><dependencies><dependency><groupId>com.aiplatform.foundation</groupId><artifactId>model-runtime</artifactId></dependency></dependencies></project>
            """,
        )
        self.write_pom(
            "backend/contexts/model-runtime/pom.xml",
            """
            <project><dependencies><dependency><groupId>com.aiplatform.foundation</groupId><artifactId>alpha</artifactId></dependency></dependencies></project>
            """,
        )

        violations = MODULE.backend_dependency_boundary_violations(self.root)

        self.assertTrue(any("循环" in violation for violation in violations))


if __name__ == "__main__":
    unittest.main()

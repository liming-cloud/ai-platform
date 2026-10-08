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

    def test_qualified_mapper_include_preserves_pagination_scan(self):
        shared = self.root / "SharedMapper.xml"
        local = self.root / "LocalMapper.xml"
        texts = {
            shared: '<mapper namespace="demo.Shared"><sql id="filter">WHERE active = TRUE</sql><sql id="bounds">LIMIT #{size}</sql></mapper>',
            local: '<mapper namespace="demo.Local"><select id="page">SELECT * FROM item <include refid="demo.Shared.filter"/></select><select id="bad">SELECT * FROM item <include refid="demo.Shared.bounds"/></select></mapper>',
        }
        self.assertEqual(["LocalMapper.xml#bad"], MODULE.manual_list_limit_statements(self.root, texts, {}))

    def test_qualified_mapper_include_cycle_and_missing_remain_rejected(self):
        mapper = self.root / "Mapper.xml"
        for reference in ["demo.Missing.filter", "demo.Local.loop"]:
            with self.subTest(reference=reference), self.assertRaises(RuntimeError):
                MODULE.manual_list_limit_statements(self.root, {mapper: f'<mapper namespace="demo.Local"><sql id="loop"><include refid="loop"/></sql><select id="page">SELECT * FROM item <include refid="{reference}"/></select></mapper>'}, {})

    def write_select(self, statement_id, body, category="SECURITY_PROJECTION"):
        mapper_path = "backend/contexts/demo/src/main/resources/mapper/demo/DemoMapper.xml"
        self.write_pom(
            mapper_path,
            f'<mapper namespace="demo.DemoMapper"><select id="{statement_id}">{body}</select></mapper>',
        )
        self.write_allowlist([{
            "path": mapper_path,
            "statementId": statement_id,
            "category": category,
            "reason": "测试受控查询语句，分页仍须遵守框架合同。",
        }])
        return mapper_path + "#" + statement_id

    def test_manual_pagination_does_not_depend_on_statement_or_parameter_names(self):
        for statement_id, body in [
            ("list", "SELECT * FROM demo LIMIT #{limit}"),
            ("findItems", "SELECT * FROM demo limit #{pageSize} offset #{offset}"),
            ("directory", "SELECT * FROM demo OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY"),
        ]:
            with self.subTest(statement_id=statement_id):
                key = self.write_select(statement_id, body)
                actual = MODULE.scan(self.root)
                self.assertEqual(1, actual["manualListLimitCount"])
                self.assertEqual([key], actual["manualListLimitStatements"])

    def test_limit_in_next_statement_does_not_contaminate_previous_select(self):
        mapper_path = "backend/contexts/demo/src/main/resources/mapper/demo/DemoMapper.xml"
        self.write_pom(mapper_path, '''<mapper namespace="demo.DemoMapper">
          <select id="selectPage">SELECT * FROM demo</select>
          <select id="claimableJob">SELECT * FROM job FOR UPDATE SKIP LOCKED LIMIT #{limit}</select>
        </mapper>''')
        self.write_allowlist([{
            "path": mapper_path, "statementId": statement_id,
            "category": category, "reason": "普通目录使用框架分页，任务领取保持内部批次协议。",
        } for statement_id, category in [("selectPage", "SECURITY_PROJECTION"), ("claimableJob", "SKIP_LOCKED")]])

        self.assertEqual(0, MODULE.scan(self.root)["manualListLimitCount"])

    def test_nested_projection_limit_and_comments_are_not_outer_pagination(self):
        self.write_select("selectPage", '''<![CDATA[
            SELECT item.*, 'LIMIT 9' AS label FROM item
            LEFT JOIN LATERAL (SELECT id FROM publication LIMIT 1) publication ON TRUE
            -- LIMIT #{limit}
            /* OFFSET #{offset} */
        ]]>''')

        self.assertEqual(0, MODULE.scan(self.root)["manualListLimitCount"])

    def test_parameter_name_alone_is_not_sql_pagination(self):
        self.write_select("selectPage", "SELECT * FROM demo WHERE attempts &lt;= #{limit}")

        self.assertEqual(0, MODULE.scan(self.root)["manualListLimitCount"])

    def test_pagination_in_sql_fragment_is_detected(self):
        mapper_path = "backend/contexts/demo/src/main/resources/mapper/demo/DemoMapper.xml"
        self.write_pom(mapper_path, '''<mapper namespace="demo.DemoMapper">
          <sql id="bounds">LIMIT #{size} OFFSET #{start}</sql>
          <select id="list">SELECT * FROM demo <include refid="bounds"/></select>
        </mapper>''')

        self.assertEqual(1, MODULE.scan(self.root)["manualListLimitCount"])

    def test_wrapping_directory_query_cannot_hide_manual_pagination(self):
        for body in [
            "SELECT * FROM (SELECT * FROM demo LIMIT #{limit} OFFSET #{offset}) page",
            "(SELECT * FROM demo LIMIT #{limit})",
            "SELECT item.* FROM item JOIN LATERAL (SELECT * FROM demo LIMIT #{size}) page ON TRUE",
        ]:
            with self.subTest(body=body):
                self.write_select("list", body)
                self.assertEqual(1, MODULE.scan(self.root)["manualListLimitCount"])

    def test_postgres_dollar_quoted_literal_is_not_pagination(self):
        self.write_select("selectPage", "SELECT $$LIMIT 1$$, $note$OFFSET 3$note$ FROM demo")

        self.assertEqual(0, MODULE.scan(self.root)["manualListLimitCount"])

    def test_same_count_cannot_replace_registered_manual_pagination(self):
        self.write_select("findPage", "SELECT * FROM demo LIMIT #{limit}")
        baseline = MODULE.scan(self.root)
        self.write_select("list", "SELECT * FROM demo LIMIT #{limit}")

        errors = MODULE.compare(MODULE.scan(self.root), baseline)

        self.assertTrue(any("手工列表分页" in error for error in errors))

    def test_removing_manual_pagination_is_allowed(self):
        self.write_select("findPage", "SELECT * FROM demo LIMIT #{limit}")
        baseline = MODULE.scan(self.root)
        self.write_select("findPage", "SELECT * FROM demo")

        self.assertEqual([], MODULE.compare(MODULE.scan(self.root), baseline))

    def test_task_claim_is_not_page_pagination(self):
        self.write_select(
            "claimableJob", "SELECT * FROM job FOR UPDATE SKIP LOCKED LIMIT 1", "SKIP_LOCKED",
        )

        self.assertEqual(0, MODULE.scan(self.root)["manualListLimitCount"])

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

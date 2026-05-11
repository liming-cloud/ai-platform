package org.fujay.ai.platform.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.Map.entry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class BackendMavenBaselineTest {

    private static final Path BACKEND_ROOT = Path.of("../..").toAbsolutePath().normalize();
    private static final List<String> REQUIRED_MODULES = List.of(
            "infrastructure/ai-platform-bom",
            "infrastructure/ai-platform-shared",
            "infrastructure/ai-platform-ai",
            "infrastructure/ai-platform-cache",
            "infrastructure/ai-platform-security",
            "infrastructure/ai-platform-persistence",
            "infrastructure/ai-platform-storage",
            "infrastructure/ai-platform-telemetry",
            "business/ai-platform-modules/tenant",
            "business/ai-platform-modules/model",
            "apps/ai-platform-boot",
            "quality/ai-platform-architecture-tests");
    private static final Map<String, String> MODULE_TYPE_DIRECTORIES = Map.ofEntries(
            entry("infrastructure/ai-platform-bom", "infrastructure"),
            entry("apps/ai-platform-boot", "apps"),
            entry("infrastructure/ai-platform-shared", "infrastructure"),
            entry("infrastructure/ai-platform-ai", "infrastructure"),
            entry("infrastructure/ai-platform-cache", "infrastructure"),
            entry("infrastructure/ai-platform-security", "infrastructure"),
            entry("infrastructure/ai-platform-persistence", "infrastructure"),
            entry("infrastructure/ai-platform-storage", "infrastructure"),
            entry("infrastructure/ai-platform-telemetry", "infrastructure"),
            entry("business/ai-platform-modules/tenant", "business"),
            entry("business/ai-platform-modules/model", "business"),
            entry("quality/ai-platform-architecture-tests", "quality"));
    private static final Set<String> VERSION_ALLOWED_ARTIFACTS = Set.of("ai-platform-parent", "ai-platform-bom");
    private static final Set<String> INFRASTRUCTURE_MODULES = Set.of(
            "infrastructure/ai-platform-bom",
            "infrastructure/ai-platform-ai",
            "infrastructure/ai-platform-cache",
            "infrastructure/ai-platform-security",
            "infrastructure/ai-platform-persistence",
            "infrastructure/ai-platform-storage",
            "infrastructure/ai-platform-telemetry",
            "infrastructure/ai-platform-shared");
    private static final List<String> DOMAIN_FORBIDDEN_IMPORTS = List.of(
            "org.springframework.",
            "com.baomidou.mybatisplus.",
            "cn.dev33.satoken.",
            "org.redisson.",
            "com.github.benmanes.caffeine.",
            "org.springframework.ai.",
            "io.minio.",
            "software.amazon.awssdk.");

    @Test
    void backendReactorDeclaresRequiredModules() throws Exception {
        var declaredModules = childTextValues(BACKEND_ROOT.resolve("pom.xml"), "modules", "module");

        assertEquals(REQUIRED_MODULES, declaredModules);
        for (String module : REQUIRED_MODULES) {
            assertTrue(Files.exists(BACKEND_ROOT.resolve(module).resolve("pom.xml")), "缺少模块 POM：" + module);
        }
    }

    @Test
    void backendModulesAreGroupedByModuleType() throws Exception {
        var declaredModules = childTextValues(BACKEND_ROOT.resolve("pom.xml"), "modules", "module");

        for (String module : declaredModules) {
            String expectedTypeDirectory = MODULE_TYPE_DIRECTORIES.get(module);
            assertTrue(expectedTypeDirectory != null, "模块未登记类型目录：" + module);
            assertEquals(expectedTypeDirectory, module.split("/", 2)[0], "模块没有按类型目录归档：" + module);
        }
    }

    @Test
    void everyMavenArtifactUsesFujayGroupAndAiPrefix() throws Exception {
        for (Path pom : Files.walk(BACKEND_ROOT).filter(path -> path.getFileName().toString().equals("pom.xml")).toList()) {
            Element project = projectElement(pom);
            String artifactId = directChildText(project, "artifactId");
            String groupId = directChildText(project, "groupId");
            if (groupId == null) {
                groupId = directChildText(firstDirectChild(project, "parent"), "groupId");
            }

            assertEquals("org.fujay", groupId, "groupId 必须固定为 org.fujay：" + pom);
            assertTrue(artifactId.startsWith("ai-"), "artifactId 必须以 ai- 开头：" + pom);
        }
    }

    @Test
    void childModulesDoNotDeclareDependencyVersions() throws Exception {
        for (Path pom : Files.walk(BACKEND_ROOT).filter(path -> path.getFileName().toString().equals("pom.xml")).toList()) {
            Element project = projectElement(pom);
            String artifactId = directChildText(project, "artifactId");
            if (VERSION_ALLOWED_ARTIFACTS.contains(artifactId)) {
                continue;
            }

            for (Element dependency : directChildren(firstDirectChild(project, "dependencies"), "dependency")) {
                if ("org.fujay".equals(directChildText(dependency, "groupId"))) {
                    continue;
                }
                assertFalse(hasDirectChild(dependency, "version"), "第三方依赖版本必须由 BOM 管理：" + pom);
            }
        }
    }

    @Test
    void platformInfrastructureModulesDoNotDependOnBusinessContexts() throws Exception {
        for (String module : INFRASTRUCTURE_MODULES) {
            Path pom = BACKEND_ROOT.resolve(module).resolve("pom.xml");
            assertTrue(Files.exists(pom), "缺少平台基础设施模块 POM：" + module);
            for (String artifactId : childTextValues(pom, "dependencies", "artifactId")) {
                assertFalse(artifactId.startsWith("ai-platform-tenant"), module + " 不得依赖业务上下文");
                assertFalse(artifactId.startsWith("ai-platform-model"), module + " 不得依赖业务上下文");
            }
        }
    }

    @Test
    void domainLayerDoesNotImportInfrastructureFrameworks() throws Exception {
        List<Path> domainFiles = javaFiles().stream()
                .filter(path -> path.toString().contains("/domain/"))
                .toList();

        assertFalse(domainFiles.isEmpty(), "至少需要存在一个 domain 层 Java 文件");
        for (Path javaFile : domainFiles) {
            String text = Files.readString(javaFile);
            for (String forbiddenImport : DOMAIN_FORBIDDEN_IMPORTS) {
                assertFalse(text.contains("import " + forbiddenImport), "domain 层不得依赖基础设施框架：" + javaFile);
            }
        }
    }

    @Test
    void satokenStpUtilIsOnlyUsedInsideSecurityInfrastructure() throws Exception {
        Path securityModule = BACKEND_ROOT.resolve("infrastructure/ai-platform-security").normalize();
        for (Path javaFile : javaFiles()) {
            String text = Files.readString(javaFile);
            if (!text.contains("StpUtil")) {
                continue;
            }

            assertTrue(javaFile.normalize().startsWith(securityModule), "StpUtil 只能由安全基础设施模块封装：" + javaFile);
        }
    }

    @Test
    void databaseMigrationsUseSnowflakeLongIdPrimaryKeys() throws Exception {
        Path migrationRoot = BACKEND_ROOT.resolve("apps/ai-platform-boot/src/main/resources/db/migration");
        List<Path> migrations = Files.walk(migrationRoot)
                .filter(path -> path.getFileName().toString().endsWith(".sql"))
                .toList();

        assertFalse(migrations.isEmpty(), "必须存在 Flyway 数据库迁移");
        for (Path migration : migrations) {
            String sql = Files.readString(migration).replaceAll("\\s+", " ").toLowerCase();
            assertFalse(sql.contains("varchar(64) primary key"), "主键不得使用字符串业务标识：" + migration);
            assertFalse(sql.contains("bigserial primary key"), "主键必须由雪花 Long 生成，不得使用数据库自增：" + migration);
            assertFalse(sql.matches(".*\\b(tenant_id|user_id|provider_id|model_id|invocation_id|audit_id)\\s+[^,;]*primary key.*"),
                    "主键字段必须统一命名为 id：" + migration);
        }

        for (String createTable : createTableBlocks(migrations)) {
            assertTrue(createTable.contains(" id bigint primary key"), "每张表必须声明 id BIGINT PRIMARY KEY：" + createTable);
        }
    }

    @Test
    void persistenceObjectsUseLongIdAndLongRelations() throws Exception {
        List<Path> persistenceObjects = javaFiles().stream()
                .filter(path -> path.toString().contains("/infrastructure/persistence/po/"))
                .toList();

        assertFalse(persistenceObjects.isEmpty(), "必须存在持久化对象");
        for (Path javaFile : persistenceObjects) {
            String text = Files.readString(javaFile);
            assertTrue(text.contains("private Long id;"), "PO 主键字段必须是 Long id：" + javaFile);
            assertFalse(text.contains("private String tenantId;"), "tenantId 是 Long 关联键，不得使用名称或编码：" + javaFile);
            assertFalse(text.contains("private String userId;"), "userId 是 Long 关联键，不得使用名称或编码：" + javaFile);
            assertFalse(text.contains("private String providerId;"), "providerId 是 Long 关联键，不得使用名称或编码：" + javaFile);
            assertFalse(text.contains("private String modelId;"), "modelId 是 Long 关联键，不得使用名称或编码：" + javaFile);
            assertFalse(text.contains("private String invocationId;"), "调用记录主键必须统一为 Long id：" + javaFile);
            assertFalse(text.contains("private Long auditId;"), "审计记录主键必须统一命名为 id：" + javaFile);
        }
    }

    private static List<Path> javaFiles() throws Exception {
        return Files.walk(BACKEND_ROOT)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .filter(path -> path.toString().contains("/src/main/java/"))
                .filter(path -> !path.toString().contains("/target/"))
                .toList();
    }

    private static List<String> childTextValues(Path pom, String parentTag, String childTag) throws Exception {
        Element parent = firstDirectChild(projectElement(pom), parentTag);
        return directChildren(parent, childTag).stream().map(Element::getTextContent).map(String::trim).toList();
    }

    private static Element projectElement(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(pom.toFile()).getDocumentElement();
    }

    private static Element firstDirectChild(Element element, String tagName) {
        if (element == null) {
            return null;
        }
        for (Element child : directChildren(element, tagName)) {
            return child;
        }
        return null;
    }

    private static String directChildText(Element element, String tagName) {
        Element child = firstDirectChild(element, tagName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static boolean hasDirectChild(Element element, String tagName) {
        return firstDirectChild(element, tagName) != null;
    }

    private static List<Element> directChildren(Element element, String tagName) {
        if (element == null) {
            return List.of();
        }
        var children = new java.util.ArrayList<Element>();
        var nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element child && child.getTagName().equals(tagName)) {
                children.add(child);
            }
        }
        return children;
    }

    private static List<String> createTableBlocks(List<Path> migrations) throws Exception {
        List<String> blocks = new ArrayList<>();
        for (Path migration : migrations) {
            String sql = Files.readString(migration).replaceAll("\\s+", " ").toLowerCase();
            int start = sql.indexOf("create table");
            while (start >= 0) {
                int end = sql.indexOf(");", start);
                if (end < 0) {
                    break;
                }
                blocks.add(sql.substring(start, end + 2));
                start = sql.indexOf("create table", end);
            }
        }
        return blocks;
    }
}

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
import java.util.regex.Pattern;
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
    private static final List<String> BUSINESS_LAYER_FORBIDDEN_PLATFORM_IMPORTS = List.of(
            "org.fujay.ai.platform.ai.",
            "org.fujay.ai.platform.security.",
            "org.fujay.ai.platform.persistence.",
            "org.fujay.ai.platform.cache.",
            "org.fujay.ai.platform.storage.",
            "org.fujay.ai.platform.telemetry.");
    private static final Pattern NESTED_RECORD = Pattern.compile("^\\s*(?:public\\s+)?record\\s+\\w+\\s*\\(",
            Pattern.MULTILINE);
    private static final Pattern REPOSITORY_RECORD_IMPORT = Pattern.compile(
            "import\\s+org\\.fujay\\.ai\\.platform\\.[^.]+\\.domain\\.repository\\.[^;]+\\.\\w+Record;");
    private static final Pattern REPOSITORY_RECORD_CONSTRUCTION = Pattern.compile("new\\s+\\w+Record\\s*\\(");
    private static final Pattern PRIVATE_CONVERSION_METHOD = Pattern.compile(
            "^\\s*private\\s+[\\w<>, ?]+\\s+(?:to|from)[A-Z]\\w*\\s*\\(", Pattern.MULTILINE);

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

    @Test
    void repositoryPortsDoNotExposeNestedRecordContracts() throws Exception {
        List<Path> repositoryPorts = javaFiles().stream()
                .filter(path -> path.toString().contains("/domain/repository/"))
                .toList();

        assertFalse(repositoryPorts.isEmpty(), "必须存在 domain repository port");
        for (Path javaFile : repositoryPorts) {
            String text = Files.readString(javaFile);
            assertFalse(NESTED_RECORD.matcher(text).find(), "repository port 不得声明内部 record 合同：" + javaFile);
        }
    }

    @Test
    void applicationServicesDoNotConsumeRepositoryRecordsOrPlatformInfrastructure() throws Exception {
        for (Path javaFile : javaFiles()) {
            String fileName = javaFile.getFileName().toString();
            String path = javaFile.toString();
            if (!path.contains("/application/service/") || !fileName.endsWith("ApplicationService.java")) {
                continue;
            }

            String text = Files.readString(javaFile);
            assertTrue(text.lines().count() <= 300, "application service 超过 300 行，疑似承载领域行为：" + javaFile);
            assertFalse(
                    REPOSITORY_RECORD_IMPORT.matcher(text).find(),
                    "application service 不得 import repository 内部 record：" + javaFile);
            assertFalse(
                    REPOSITORY_RECORD_CONSTRUCTION.matcher(text).find(),
                    "application service 不得构造 repository record：" + javaFile);
            assertFalse(text.contains("SnowflakeIdGenerator"), "application service 不得直接生成领域 ID：" + javaFile);
            if (path.contains("/business/ai-platform-modules/model/")) {
                assertFalse(
                        text.contains("import org.fujay.ai.platform.ai."),
                        "model application service 不得直接依赖平台 AI facade：" + javaFile);
                assertFalse(
                        text.contains("import org.fujay.ai.platform.security.SecretCipher;"),
                        "model application service 不得直接处理 provider 密钥：" + javaFile);
            }
        }
    }

    @Test
    void businessApplicationAndDomainLayersOnlyDependOnLocalPortsOrSharedKernel() throws Exception {
        for (Path javaFile : javaFiles()) {
            String path = javaFile.toString();
            if (!path.contains("/business/ai-platform-modules/")
                    || !(path.contains("/application/") || path.contains("/domain/"))) {
                continue;
            }

            String text = Files.readString(javaFile);
            for (String forbiddenImport : BUSINESS_LAYER_FORBIDDEN_PLATFORM_IMPORTS) {
                assertFalse(
                        text.contains("import " + forbiddenImport),
                        "业务 application/domain 不得直接依赖平台基础设施模块，应通过本模块 port + infrastructure adapter 接入："
                                + javaFile);
            }
        }
    }

    @Test
    void tenantAccessDoesNotUseRedundantApplicationFacadeOrGodRepository() throws Exception {
        Path tenantRoot = BACKEND_ROOT.resolve("business/ai-platform-modules/tenant/src/main/java/org/fujay/ai/platform/tenant");

        assertFalse(
                Files.exists(tenantRoot.resolve("application/service/TenantAccessApplicationService.java")),
                "TenantAccessApplicationService 只是总入口转发时应删除，controller 直接调用具体 application service");
        assertFalse(
                Files.exists(tenantRoot.resolve("domain/repository/TenantAccessRepository.java")),
                "TenantAccessRepository 不得作为跨租户、用户、IAM、审计和 bootstrap 的总仓储");
        assertFalse(
                Files.exists(tenantRoot.resolve("infrastructure/persistence/repository/MybatisPlusTenantAccessRepository.java")),
                "MybatisPlusTenantAccessRepository 不得作为万能 MyBatis-Plus repository 实现");
    }

    @Test
    void tenantModuleDoesNotBootstrapAdminAccountOnStartup() throws Exception {
        Path tenantBootstrapRoot = BACKEND_ROOT.resolve(
                "business/ai-platform-modules/tenant/src/main/java/org/fujay/ai/platform/tenant/infrastructure/bootstrap");
        if (!Files.exists(tenantBootstrapRoot)) {
            return;
        }

        for (Path javaFile : Files.walk(tenantBootstrapRoot)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .toList()) {
            String text = Files.readString(javaFile);
            assertFalse(text.contains("ApplicationRunner"), "tenant 模块不得在启动时初始化管理员账号：" + javaFile);
            assertFalse(text.contains("CommandLineRunner"), "tenant 模块不得在启动时初始化管理员账号：" + javaFile);
            assertFalse(text.contains("@PostConstruct"), "tenant 模块不得在 Bean 初始化时创建管理员账号：" + javaFile);
            assertFalse(text.contains("bootstrapTenantUser"), "管理员初始化应由 Flyway SQL 一次性完成：" + javaFile);
        }
    }

    @Test
    void persistenceRepositoryImplementationsStayFocusedOnOnePersistenceConcern() throws Exception {
        for (Path javaFile : javaFiles()) {
            String path = javaFile.toString();
            if (!path.contains("/infrastructure/persistence/repository/")) {
                continue;
            }

            String text = Files.readString(javaFile);
            assertTrue(text.lines().count() <= 300, "repository impl 不得成为超大多聚合实现类：" + javaFile);
            assertFalse(text.contains("private record "), "repository impl 不得用 private record 承载业务事实：" + javaFile);
            assertFalse(text.contains("DEFAULT_"), "repository impl 不得拥有默认 seed/catalog 数据：" + javaFile);
            long conversionMethodCount = PRIVATE_CONVERSION_METHOD.matcher(text).results().count();
            assertTrue(conversionMethodCount <= 6, "repository impl 私有转换方法过多，应拆 converter：" + javaFile);
        }
    }

    @Test
    void persistenceStoresStayFocusedAndDoNotBecomeRepositoryWorkarounds() throws Exception {
        List<Path> storeFiles = javaFiles().stream()
                .filter(path -> path.toString().contains("/business/ai-platform-modules/"))
                .filter(path -> path.toString().contains("/infrastructure/persistence/store/"))
                .toList();

        assertTrue(storeFiles.isEmpty(), "repository 已按业务拆分后不得再保留 persistence store 层：" + storeFiles);
    }

    @Test
    void sharedKernelProvidesDddStereotypeMarkers() {
        Path markerRoot = BACKEND_ROOT.resolve(
                "infrastructure/ai-platform-shared/src/main/java/org/fujay/ai/platform/shared/ddd");

        for (String marker : List.of("DomainObject", "AggregateRoot", "Entity", "ValueObject")) {
            assertTrue(Files.exists(markerRoot.resolve(marker + ".java")), "shared 必须提供 DDD 标记接口：" + marker);
        }
    }

    @Test
    void domainModelTypesDeclareDddStereotypeMarkers() throws Exception {
        for (Path javaFile : javaFiles()) {
            String path = javaFile.toString();
            if (!path.contains("/business/ai-platform-modules/")
                    || !(path.contains("/domain/aggregate/")
                            || path.contains("/domain/model/")
                            || path.contains("/domain/value/")
                            || path.contains("/domain/valueobject/"))) {
                continue;
            }

            String text = Files.readString(javaFile);
            if (path.contains("/domain/aggregate/")) {
                assertTrue(declaresDddMarker(text, "AggregateRoot"), "domain aggregate 必须声明 AggregateRoot 标记："
                        + javaFile);
                continue;
            }
            if (path.contains("/domain/value/") || path.contains("/domain/valueobject/")) {
                assertTrue(declaresDddMarker(text, "ValueObject"), "domain value object 必须声明 ValueObject 标记："
                        + javaFile);
                continue;
            }
            assertTrue(
                    declaresDddMarker(text, "AggregateRoot")
                            || declaresDddMarker(text, "Entity")
                            || declaresDddMarker(text, "ValueObject"),
                    "domain model 不得作为无语义 record/class 存在，必须声明 AggregateRoot/Entity/ValueObject："
                            + javaFile);
        }
    }

    @Test
    void bootstrapRunnersDoNotBypassApplicationAndDomainBoundaries() throws Exception {
        for (Path javaFile : javaFiles()) {
            String path = javaFile.toString();
            if (!path.contains("/infrastructure/bootstrap/")) {
                continue;
            }

            String text = Files.readString(javaFile);
            assertFalse(
                    text.contains(".infrastructure.persistence.mapper.") || text.contains(".infrastructure.persistence.po."),
                    "bootstrap runner 不得直接 import Mapper/PO：" + javaFile);
            assertFalse(
                    REPOSITORY_RECORD_IMPORT.matcher(text).find()
                            || REPOSITORY_RECORD_CONSTRUCTION.matcher(text).find(),
                    "bootstrap runner 不得构造 repository record 绕过 application/domain：" + javaFile);
        }
    }

    @Test
    void productionConfigurationDoesNotShipLocalSecretFallbacks() throws Exception {
        Path applicationYaml = BACKEND_ROOT.resolve("apps/ai-platform-boot/src/main/resources/application.yml");
        Path secretCipher = BACKEND_ROOT.resolve(
                "infrastructure/ai-platform-security/src/main/java/org/fujay/ai/platform/security/SecretCipher.java");

        String yaml = Files.readString(applicationYaml);
        String cipher = Files.readString(secretCipher);
        assertFalse(yaml.contains("ai-platform-local-secret-change-me"), "主配置不得内置本地 master key 默认值");
        assertFalse(yaml.contains("master-key: ${AI_PLATFORM_SECRET_MASTER_KEY:"), "主配置必须显式提供 master key");
        assertFalse(cipher.contains("ai-platform-local-secret-change-me"), "SecretCipher 不得在代码中内置本地 master key 默认值");
        assertFalse(cipher.contains("${ai.platform.secret.master-key:"), "SecretCipher 必须依赖显式配置的 master key");
    }

    private static List<Path> javaFiles() throws Exception {
        List<Path> files = new ArrayList<>();
        for (String module : REQUIRED_MODULES) {
            Path sourceRoot = BACKEND_ROOT.resolve(module).resolve("src/main/java");
            if (!Files.exists(sourceRoot)) {
                continue;
            }
            try (var sourceFiles = Files.walk(sourceRoot)) {
                files.addAll(sourceFiles
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .toList());
            }
        }
        return files;
    }

    private static boolean declaresDddMarker(String source, String marker) {
        return Pattern.compile("\\bimplements\\b[^{;]*\\b" + marker + "\\b").matcher(source).find();
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

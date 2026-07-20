package com.aiplatform.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DevelopmentRuleTest {
    private static final Path BACKEND_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final List<String> P1_CONTEXTS = List.of(
            "identity-access",
            "model-hub",
            "knowledge-base",
            "app-studio"
    );
    private static final Pattern INNER_DATA_CARRIER = Pattern.compile(
            "\\b(public|private|protected)?\\s*(static\\s+)?(final\\s+)?(class|record)\\s+\\w+(Request|Response|Result|View|Command)\\b"
    );
    private static final Pattern MYBATIS_SQL_ANNOTATION = Pattern.compile(
            "@(Select|Insert|Update|Delete|SelectProvider|InsertProvider|UpdateProvider|DeleteProvider)\\b"
    );
    private static final Pattern NESTED_TYPE_DECLARATION = Pattern.compile(
            "^(?:public|private|protected|static|final|sealed|non-sealed|abstract|record|class|interface|enum)"
                    + "(?:\\s+(?:static|final|abstract|sealed|non-sealed))*\\s+"
                    + "(?:class|record|interface|enum)\\b"
    );
    private static final Pattern ROCKETMQ_IDENTIFIER_TOKEN = Pattern.compile("\\b(?:topic|cg)[A-Za-z0-9_.-]*\\b");
    private static final Pattern ROCKETMQ_IDENTIFIER = Pattern.compile("^[%|A-Za-z0-9_-]+$");

    @Test
    void controllersAndServicesMustNotDeclareInnerDataCarriers() throws IOException {
        try (Stream<Path> files = Files.walk(BACKEND_ROOT.resolve("contexts"))) {
            List<String> violations = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/presentation/") || path.toString().contains("/application/"))
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java")
                            || path.getFileName().toString().endsWith("Service.java"))
                    .flatMap(DevelopmentRuleTest::innerDataCarrierViolations)
                    .toList();

            assertThat(violations)
                    .as("Controller 和 Service 必须使用顶层 DTO、Command、Result 类型")
                    .isEmpty();
        }
    }

    @Test
    void productionJavaMustNotDeclareNestedTypes() throws IOException {
        try (Stream<Path> files = Files.walk(BACKEND_ROOT)) {
            List<String> violations = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .flatMap(DevelopmentRuleTest::nestedTypeViolations)
                    .toList();

            assertThat(violations)
                    .as("生产 Java 类型必须顶层建模，禁止用内部类规避 DDD 归属判断")
                    .isEmpty();
        }
    }

    @Test
    void p1ContextsMustDefineDomainAndPortPackagesBeforeApiImplementation() {
        List<String> missingPackages = P1_CONTEXTS.stream()
                .flatMap(context -> Stream.of(
                        "contexts/%s/src/main/java/%s/domain".formatted(context, packagePath(context)),
                        "contexts/%s/src/main/java/%s/domain/port".formatted(context, packagePath(context))
                ))
                .map(BACKEND_ROOT::resolve)
                .filter(path -> !Files.isDirectory(path))
                .map(BACKEND_ROOT::relativize)
                .map(Path::toString)
                .toList();

        assertThat(missingPackages)
                .as("DDD 研发必须先建立 domain 与 domain.port 包，再进入 Controller 实现")
                .isEmpty();
    }

    @Test
    void mybatisSqlMustBeDeclaredInXmlMapperFiles() throws IOException {
        try (Stream<Path> files = Files.walk(BACKEND_ROOT)) {
            List<String> violations = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(DevelopmentRuleTest::mybatisSqlAnnotationViolations)
                    .toList();

            assertThat(violations)
                    .as("SQL 必须声明在 mapper XML 中，禁止使用 MyBatis SQL 注解")
                    .isEmpty();
        }
    }

    @Test
    void persistencePackagesMustBeLayered() throws IOException {
        try (Stream<Path> files = Files.walk(BACKEND_ROOT.resolve("contexts"))) {
            List<String> violations = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(DevelopmentRuleTest::isJavaFileDirectlyUnderPersistencePackage)
                    .map(path -> BACKEND_ROOT.relativize(path.toAbsolutePath().normalize()).toString())
                    .toList();

            assertThat(violations)
                    .as("持久化代码必须放在 entity、mapper、repository 等子包下")
                    .isEmpty();
        }
    }

    @Test
    void platformBootMustOnlyContainStartupHealthAndConfigurationCode() throws IOException {
        Path root = BACKEND_ROOT.resolve("platform-boot/src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            List<String> violations = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(DevelopmentRuleTest::isForbiddenPlatformBootProductionFile)
                    .map(path -> BACKEND_ROOT.relativize(path.toAbsolutePath().normalize()).toString())
                    .toList();

            assertThat(violations)
                    .as("platform-boot 只能承载启动装配、配置和健康检查，业务功能必须留在所属上下文")
                    .isEmpty();
        }
    }

    @Test
    void independentPlatformIntegrationModuleMustNotExist() {
        Path platformIntegration = BACKEND_ROOT.resolve("platform-integration");

        assertThat(Files.exists(platformIntegration))
                .as("跨上下文协作默认通过 Spring Cloud Stream 领域事件和本地投影，禁止独立平台同步组合模块")
                .isFalse();
    }

    @Test
    void domainFlowModelPackagesMustNotExist() throws IOException {
        try (Stream<Path> files = Files.walk(BACKEND_ROOT.resolve("contexts"))) {
            List<String> violations = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(DevelopmentRuleTest::isForbiddenDomainFlowModelPackage)
                    .map(path -> BACKEND_ROOT.relativize(path.toAbsolutePath().normalize()).toString())
                    .toList();

            assertThat(violations)
                    .as("流程型端口模型必须放在 domain.port 下，禁止 domain/ingest/model 等弱领域目录")
                    .isEmpty();
        }
    }

    @Test
    void boundedContextsMustNotDependOnOtherContextModules() throws IOException {
        try (Stream<Path> poms = Files.walk(BACKEND_ROOT.resolve("contexts"), 2)) {
            List<String> violations = poms
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .flatMap(DevelopmentRuleTest::contextModuleDependencyViolations)
                    .toList();

            assertThat(violations)
                    .as("限界上下文之间必须通过领域事件、MQ 或受审同步端口协作，禁止直接依赖其他上下文")
                    .isEmpty();
        }
    }

    @Test
    void rocketmqTopicAndConsumerGroupNamesMustUseClientSafeCharacters() throws IOException {
        List<Path> roots = List.of(
                BACKEND_ROOT,
                BACKEND_ROOT.getParent().resolve("deploy"),
                BACKEND_ROOT.getParent().resolve("artifacts/mq-design")
        );
        List<String> violations = new ArrayList<>();
        for (Path root : roots) {
            if (Files.exists(root)) {
                try (Stream<Path> files = Files.walk(root)) {
                    files.filter(Files::isRegularFile)
                            .filter(DevelopmentRuleTest::isTextConfigurationFile)
                            .flatMap(DevelopmentRuleTest::rocketmqIdentifierViolations)
                            .forEach(violations::add);
                }
            }
        }

        assertThat(violations)
                .as("RocketMQ topic 和 consumer group 命名必须通过客户端字符校验，禁止使用点号等非法字符")
                .isEmpty();
    }

    @Test
    void messagingAdaptersMustNotUseConditionalOnBeanForRuntimeEnablement() throws IOException {
        try (Stream<Path> files = Files.walk(BACKEND_ROOT)) {
            List<String> violations = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(DevelopmentRuleTest::isMessagingRuntimeAdapter)
                    .filter(DevelopmentRuleTest::containsConditionalOnBean)
                    .map(path -> BACKEND_ROOT.getParent().relativize(path.toAbsolutePath().normalize()).toString())
                    .toList();

            assertThat(violations)
                    .as("MQ 运行时适配器必须按显式配置属性启用，禁止用 @ConditionalOnBean 隐式依赖注册顺序")
                    .isEmpty();
        }
    }

    @Test
    void everyProtectedControllerRouteMustHaveExactlyOnePermissionRule() throws IOException {
        assertThat(RoutePermissionCoverageGate.applicationViolations())
                .as("每个受保护 Controller 路由必须且只能对应一条 application.yml 权限规则，配置中也不得残留失效路由")
                .isEmpty();
    }

    private static Stream<String> innerDataCarrierViolations(Path path) {
        try {
            String source = Files.readString(path);
            if (!INNER_DATA_CARRIER.matcher(source).find()) {
                return Stream.empty();
            }
            return Stream.of(BACKEND_ROOT.relativize(path.toAbsolutePath().normalize()).toString());
        } catch (IOException exception) {
            throw new IllegalStateException("检查内部数据载体失败: " + path, exception);
        }
    }

    private static Stream<String> nestedTypeViolations(Path path) {
        try {
            List<String> violations = new ArrayList<>();
            int depth = 0;
            List<String> lines = Files.readAllLines(path);
            for (int index = 0; index < lines.size(); index++) {
                String stripped = lines.get(index).trim();
                if (depth > 0 && isNestedTypeDeclaration(stripped)) {
                    violations.add(BACKEND_ROOT.relativize(path.toAbsolutePath().normalize())
                            + ":" + (index + 1) + " " + stripped);
                }
                depth += count(lines.get(index), '{') - count(lines.get(index), '}');
            }
            return violations.stream();
        } catch (IOException exception) {
            throw new IllegalStateException("检查内部类型失败: " + path, exception);
        }
    }

    private static boolean isNestedTypeDeclaration(String stripped) {
        return !stripped.startsWith("*")
                && !stripped.startsWith("//")
                && NESTED_TYPE_DECLARATION.matcher(stripped).find();
    }

    private static int count(String value, char target) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == target) {
                count++;
            }
        }
        return count;
    }

    private static Stream<String> mybatisSqlAnnotationViolations(Path path) {
        try {
            String source = Files.readString(path);
            if (!MYBATIS_SQL_ANNOTATION.matcher(source).find()) {
                return Stream.empty();
            }
            return Stream.of(BACKEND_ROOT.relativize(path.toAbsolutePath().normalize()).toString());
        } catch (IOException exception) {
            throw new IllegalStateException("检查 MyBatis SQL 注解失败: " + path, exception);
        }
    }

    private static boolean isJavaFileDirectlyUnderPersistencePackage(Path path) {
        Path parent = path.getParent();
        return parent != null && parent.getFileName() != null && "persistence".equals(parent.getFileName().toString());
    }

    private static boolean isForbiddenDomainFlowModelPackage(Path path) {
        String normalized = path.toAbsolutePath().normalize().toString();
        return normalized.contains("/domain/ingest/model/")
                || normalized.contains("/domain/process/model/")
                || normalized.contains("/domain/workflow/model/");
    }

    private static boolean isForbiddenPlatformBootProductionFile(Path path) {
        String normalized = path.toAbsolutePath().normalize().toString();
        String fileName = path.getFileName().toString();
        if ("AiPlatformApplication.java".equals(fileName)) {
            return false;
        }
        return !normalized.contains("/com/aiplatform/boot/health/")
                && !normalized.contains("/com/aiplatform/boot/config/");
    }

    private static Stream<String> contextModuleDependencyViolations(Path pom) {
        try {
            String source = Files.readString(pom);
            String context = pom.getParent().getFileName().toString();
            List<String> violations = new ArrayList<>();
            for (String candidate : contextModuleNames()) {
                if (!candidate.equals(context)
                        && source.contains("<artifactId>" + candidate + "</artifactId>")) {
                    violations.add(BACKEND_ROOT.relativize(pom.toAbsolutePath().normalize())
                            + " depends on contexts/" + candidate);
                }
            }
            return violations.stream();
        } catch (IOException exception) {
            throw new IllegalStateException("检查 Maven 模块依赖失败: " + pom, exception);
        }
    }

    private static List<String> contextModuleNames() {
        return List.of(
                "identity-access",
                "model-hub",
                "knowledge-base",
                "app-studio",
                "approval-center",
                "observability-audit"
        );
    }

    private static String packagePath(String context) {
        return switch (context) {
            case "identity-access" -> "com/aiplatform/identity";
            case "model-hub" -> "com/aiplatform/modelhub";
            case "knowledge-base" -> "com/aiplatform/knowledge";
            case "app-studio" -> "com/aiplatform/appstudio";
            default -> throw new IllegalArgumentException("未知上下文: " + context);
        };
    }

    private static boolean isTextConfigurationFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".java")
                || fileName.endsWith(".yml")
                || fileName.endsWith(".yaml")
                || fileName.endsWith(".json")
                || fileName.endsWith(".md")
                || fileName.endsWith(".sh");
    }

    private static Stream<String> rocketmqIdentifierViolations(Path path) {
        try {
            List<String> violations = new ArrayList<>();
            if (path.toString().contains("/target/")
                    || path.toString().contains("/node_modules/")
                    || path.toString().contains("/dist/")
                    || path.toString().contains("/build/")) {
                return Stream.empty();
            }
            List<String> lines = Files.readAllLines(path);
            for (int index = 0; index < lines.size(); index++) {
                var matcher = ROCKETMQ_IDENTIFIER_TOKEN.matcher(lines.get(index));
                while (matcher.find()) {
                    String token = matcher.group();
                    if (token.endsWith(".java")
                            || token.endsWith(".json")
                            || token.endsWith(".md")
                            || token.endsWith(".yaml")
                            || token.endsWith(".yml")
                            || token.endsWith(".sh")) {
                        continue;
                    }
                    if (!ROCKETMQ_IDENTIFIER.matcher(token).matches()) {
                        violations.add(BACKEND_ROOT.getParent().relativize(path.toAbsolutePath().normalize())
                                + ":" + (index + 1) + " " + token);
                    }
                }
            }
            return violations.stream();
        } catch (IOException exception) {
            throw new IllegalStateException("检查 RocketMQ 命名失败: " + path, exception);
        }
    }

    private static boolean isMessagingRuntimeAdapter(Path path) {
        String normalized = path.toString();
        return normalized.contains("/infrastructure/messaging/")
                || normalized.contains("/platform-infrastructure/infra-rocketmq/");
    }

    private static boolean containsConditionalOnBean(Path path) {
        try {
            return Files.readString(path).contains("@ConditionalOnBean");
        } catch (IOException exception) {
            throw new IllegalStateException("检查 MQ 条件装配失败: " + path, exception);
        }
    }
}

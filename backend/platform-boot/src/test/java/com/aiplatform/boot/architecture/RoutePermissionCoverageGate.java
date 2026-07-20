package com.aiplatform.boot.architecture;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.pattern.PathPatternParser;

final class RoutePermissionCoverageGate {
    private static final String ROUTE_PERMISSION_PREFIX = "aip.security.route-permissions";
    private static final Pattern ROUTE_PERMISSION_PROPERTY = Pattern.compile(
            "^aip\\.security\\.route-permissions\\[(\\d+)]\\.(method|pattern|permission)$"
    );
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^/]+}");
    private static final Set<RouteKey> PUBLIC_ROUTES = Set.of(
            routeKey("POST", "/api/v1/auth/login"),
            routeKey("POST", "/api/v1/auth/activate"),
            routeKey("GET", "/api/v1/health")
    );

    private RoutePermissionCoverageGate() {
    }

    static List<String> applicationViolations() {
        List<String> violations = new ArrayList<>();
        List<ControllerRouteDeclaration> controllerRoutes = discoverControllerRoutes(violations);
        List<RoutePermissionDeclaration> permissionRules = loadPermissionRules(violations);
        violations.addAll(validate(controllerRoutes, permissionRules, PUBLIC_ROUTES));
        return violations.stream().sorted().toList();
    }

    static List<RoutePermissionDeclaration> applicationPermissionRules() {
        List<String> violations = new ArrayList<>();
        List<RoutePermissionDeclaration> rules = loadPermissionRules(violations);
        if (!violations.isEmpty()) {
            throw new IllegalStateException(String.join("; ", violations));
        }
        return rules;
    }

    static List<String> validate(
            List<ControllerRouteDeclaration> controllerRoutes,
            List<RoutePermissionDeclaration> permissionRules,
            Set<RouteKey> publicRoutes
    ) {
        List<String> violations = new ArrayList<>();
        Map<RouteKey, List<ControllerRouteDeclaration>> controllersByRoute = controllerRoutes.stream()
                .collect(Collectors.groupingBy(
                        ControllerRouteDeclaration::key,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<RouteKey, List<RoutePermissionDeclaration>> rulesByRoute = permissionRules.stream()
                .collect(Collectors.groupingBy(
                        RoutePermissionDeclaration::key,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        sortedEntries(controllersByRoute).stream()
                .filter(entry -> entry.getValue().size() != 1)
                .map(entry -> "Controller 路由重复: " + entry.getKey()
                        + " <- " + controllerSources(entry.getValue()))
                .forEach(violations::add);

        sortedEntries(rulesByRoute).stream()
                .filter(entry -> entry.getValue().size() != 1)
                .map(entry -> "权限规则重复: " + entry.getKey()
                        + " <- " + permissionSources(entry.getValue()))
                .forEach(violations::add);

        publicRoutes.stream()
                .sorted(Comparator.comparing(RouteKey::toString))
                .filter(route -> controllersByRoute.getOrDefault(route, List.of()).size() != 1)
                .map(route -> "公开路由不存在或重复: " + route)
                .forEach(violations::add);

        Set<String> publicPaths = publicRoutes.stream()
                .map(RouteKey::pattern)
                .collect(Collectors.toSet());
        sortedEntries(controllersByRoute).stream()
                .map(Map.Entry::getKey)
                .filter(route -> publicPaths.contains(route.pattern()) && !publicRoutes.contains(route))
                .map(route -> "公开路径暴露了未允许的 HTTP 方法: " + route)
                .forEach(violations::add);

        Map<RouteKey, List<ControllerRouteDeclaration>> protectedControllers = controllersByRoute.entrySet().stream()
                .filter(entry -> !publicPaths.contains(entry.getKey().pattern()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        for (Map.Entry<RouteKey, List<ControllerRouteDeclaration>> entry : sortedEntries(protectedControllers)) {
            RouteKey route = entry.getKey();
            List<RoutePermissionDeclaration> exactRules = rulesByRoute.getOrDefault(route, List.of());
            if (exactRules.isEmpty()) {
                violations.add("缺少显式权限规则: " + route
                        + " <- " + controllerSources(entry.getValue()));
            }

            String samplePath = entry.getValue().getFirst().samplePath();
            List<RoutePermissionDeclaration> runtimeMatches = permissionRules.stream()
                    .filter(rule -> rule.key().method().equals(route.method()))
                    .filter(rule -> matchesRuntimePath(rule.key().pattern(), samplePath))
                    .toList();
            boolean shouldReportRuntimeMatchCount = runtimeMatches.size() > 1
                    || (!exactRules.isEmpty() && runtimeMatches.size() != 1);
            if (shouldReportRuntimeMatchCount) {
                violations.add("运行时权限规则匹配数不是 1: " + route
                        + " -> " + permissionSources(runtimeMatches));
            }
        }

        sortedEntries(rulesByRoute).stream()
                .filter(entry -> !protectedControllers.containsKey(entry.getKey()))
                .map(entry -> "权限规则未精确对应 Controller: " + entry.getKey()
                        + " <- " + permissionSources(entry.getValue()))
                .forEach(violations::add);

        return violations.stream().sorted().toList();
    }

    static ControllerRouteDeclaration controllerRoute(String method, String path, String source) {
        String normalizedPath = normalizePath(path);
        return new ControllerRouteDeclaration(
                routeKey(method, canonicalPattern(normalizedPath)),
                samplePath(normalizedPath),
                source
        );
    }

    static RoutePermissionDeclaration permissionRule(
            String method,
            String pattern,
            String permission,
            int index
    ) {
        return new RoutePermissionDeclaration(
                routeKey(method, normalizePath(pattern)),
                permission,
                index
        );
    }

    static RouteKey routeKey(String method, String pattern) {
        return new RouteKey(method.toUpperCase(Locale.ROOT), normalizePath(pattern));
    }

    private static List<ControllerRouteDeclaration> discoverControllerRoutes(List<String> violations) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        return scanner.findCandidateComponents("com.aiplatform").stream()
                .map(definition -> loadControllerClass(definition.getBeanClassName()))
                .sorted(Comparator.comparing(Class::getName))
                .flatMap(controller -> controllerRoutes(controller, violations).stream())
                .toList();
    }

    private static Class<?> loadControllerClass(String className) {
        if (className == null || className.isBlank()) {
            throw new IllegalStateException("扫描到无类名的 Controller BeanDefinition");
        }
        try {
            return Class.forName(className, false, RoutePermissionCoverageGate.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new IllegalStateException("加载 Controller 失败: " + className, exception);
        }
    }

    private static List<ControllerRouteDeclaration> controllerRoutes(
            Class<?> controller,
            List<String> violations
    ) {
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(
                controller,
                RequestMapping.class
        );
        List<String> classPaths = mappingPaths(classMapping);
        Map<Method, RequestMapping> methodMappings = MethodIntrospector.selectMethods(
                controller,
                (MethodIntrospector.MetadataLookup<RequestMapping>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class)
        );
        List<ControllerRouteDeclaration> routes = new ArrayList<>();

        methodMappings.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Method::toGenericString)))
                .forEach(entry -> addMethodRoutes(
                        controller,
                        classPaths,
                        entry.getKey(),
                        entry.getValue(),
                        routes,
                        violations
                ));
        return routes;
    }

    private static void addMethodRoutes(
            Class<?> controller,
            List<String> classPaths,
            Method method,
            RequestMapping methodMapping,
            List<ControllerRouteDeclaration> routes,
            List<String> violations
    ) {
        List<String> methodPaths = mappingPaths(methodMapping);
        List<String> combinedPaths = classPaths.stream()
                .flatMap(classPath -> methodPaths.stream().map(methodPath -> joinPaths(classPath, methodPath)))
                .toList();
        RequestMethod[] methods = methodMapping.method();
        String source = controller.getSimpleName() + "#" + method.getName();
        if (methods.length == 0) {
            combinedPaths.stream()
                    .map(path -> "Controller 路由未显式声明 HTTP 方法: " + path + " <- " + source)
                    .forEach(violations::add);
            return;
        }
        for (RequestMethod requestMethod : methods) {
            combinedPaths.stream()
                    .map(path -> controllerRoute(requestMethod.name(), path, source))
                    .forEach(routes::add);
        }
    }

    private static List<String> mappingPaths(RequestMapping mapping) {
        if (mapping == null) {
            return List.of("");
        }
        String[] paths = mapping.path().length == 0 ? mapping.value() : mapping.path();
        return paths.length == 0 ? List.of("") : Arrays.asList(paths);
    }

    private static List<RoutePermissionDeclaration> loadPermissionRules(List<String> violations) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();
        if (properties == null) {
            violations.add("无法读取 application.yml 权限配置");
            return List.of();
        }

        Set<Integer> indexes = new TreeSet<>();
        for (String propertyName : properties.stringPropertyNames()) {
            Matcher matcher = ROUTE_PERMISSION_PROPERTY.matcher(propertyName);
            if (matcher.matches()) {
                indexes.add(Integer.parseInt(matcher.group(1)));
            }
        }
        if (indexes.isEmpty()) {
            violations.add("application.yml 未配置任何路由权限规则");
            return List.of();
        }

        List<RoutePermissionDeclaration> rules = new ArrayList<>();
        for (int index : indexes) {
            String propertyPrefix = ROUTE_PERMISSION_PREFIX + "[" + index + "]";
            String method = properties.getProperty(propertyPrefix + ".method");
            String pattern = properties.getProperty(propertyPrefix + ".pattern");
            String permission = properties.getProperty(propertyPrefix + ".permission");
            if (isBlank(method) || isBlank(pattern) || isBlank(permission)) {
                violations.add("权限规则字段不完整: application.yml[" + index + "]");
                continue;
            }
            rules.add(permissionRule(method, pattern, permission, index));
        }
        return rules;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean matchesRuntimePath(String pattern, String path) {
        return PathPatternParser.defaultInstance
                .parse(pattern)
                .matches(PathContainer.parsePath(path));
    }

    private static String joinPaths(String classPath, String methodPath) {
        return normalizePath("/" + classPath + "/" + methodPath);
    }

    private static String normalizePath(String path) {
        String normalized = path == null ? "" : path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        normalized = normalized.replaceAll("/{2,}", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String canonicalPattern(String path) {
        return PATH_VARIABLE.matcher(path).replaceAll("*");
    }

    private static String samplePath(String path) {
        return PATH_VARIABLE.matcher(path).replaceAll("__route_variable__");
    }

    private static <T> List<Map.Entry<RouteKey, List<T>>> sortedEntries(Map<RouteKey, List<T>> entries) {
        return entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(RouteKey::toString)))
                .toList();
    }

    private static String controllerSources(List<ControllerRouteDeclaration> routes) {
        return routes.stream()
                .map(ControllerRouteDeclaration::source)
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String permissionSources(List<RoutePermissionDeclaration> rules) {
        return rules.stream()
                .map(RoutePermissionDeclaration::source)
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }
}

record RouteKey(String method, String pattern) {
    @Override
    public String toString() {
        return method + " " + pattern;
    }
}

record ControllerRouteDeclaration(RouteKey key, String samplePath, String source) {
}

record RoutePermissionDeclaration(RouteKey key, String permission, int index) {
    String source() {
        return "application.yml[" + index + "]";
    }
}

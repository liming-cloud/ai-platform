package com.aiplatform.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoutePermissionCoverageGateTest {
    @Test
    void releaseEvidenceRoutesRequireReadWhileRollbackRemainsPublishOnly() {
        List<RoutePermissionDeclaration> permissionRules =
                RoutePermissionCoverageGate.applicationPermissionRules();

        assertThat(permissionRules)
                .filteredOn(rule -> rule.key().equals(RoutePermissionCoverageGate.routeKey(
                        "GET",
                        "/api/v1/ai-applications/*/releases"
                )))
                .extracting(RoutePermissionDeclaration::permission)
                .containsExactly("app-studio:application:read");
        assertThat(permissionRules)
                .filteredOn(rule -> rule.key().equals(RoutePermissionCoverageGate.routeKey(
                        "GET",
                        "/api/v1/ai-applications/*/releases/*"
                )))
                .extracting(RoutePermissionDeclaration::permission)
                .containsExactly("app-studio:application:read");
        assertThat(permissionRules)
                .filteredOn(rule -> rule.key().equals(RoutePermissionCoverageGate.routeKey(
                        "POST",
                        "/api/v1/ai-applications/*/releases/*/rollback"
                )))
                .extracting(RoutePermissionDeclaration::permission)
                .containsExactly("app-studio:application:publish");
    }

    @Test
    void permissionRequestAdministrationRoutesRequireManagePermission() {
        List<RoutePermissionDeclaration> permissionRules =
                RoutePermissionCoverageGate.applicationPermissionRules();

        assertThat(permissionRules)
                .filteredOn(rule -> rule.key().equals(RoutePermissionCoverageGate.routeKey(
                        "GET",
                        "/api/v1/permission-requests"
                )))
                .extracting(RoutePermissionDeclaration::permission)
                .containsExactly("identity-access:permission-request:manage");
        assertThat(permissionRules)
                .filteredOn(rule -> rule.key().equals(RoutePermissionCoverageGate.routeKey(
                        "POST",
                        "/api/v1/permission-requests/*/actions"
                )))
                .extracting(RoutePermissionDeclaration::permission)
                .containsExactly("identity-access:permission-request:manage");
    }

    @Test
    void reportsMissingDuplicateOverlappingStaleAndPublicPathViolations() {
        List<ControllerRouteDeclaration> controllerRoutes = List.of(
                RoutePermissionCoverageGate.controllerRoute(
                        "GET",
                        "/api/v1/widgets/{widgetId}",
                        "WidgetController#read"
                ),
                RoutePermissionCoverageGate.controllerRoute(
                        "POST",
                        "/api/v1/widgets",
                        "WidgetController#create"
                ),
                RoutePermissionCoverageGate.controllerRoute(
                        "POST",
                        "/api/v1/widgets",
                        "DuplicateWidgetController#create"
                ),
                RoutePermissionCoverageGate.controllerRoute(
                        "GET",
                        "/api/v1/public-entry",
                        "PublicController#wrongMethod"
                )
        );
        List<RoutePermissionDeclaration> permissionRules = List.of(
                RoutePermissionCoverageGate.permissionRule(
                        "GET",
                        "/api/v1/widgets/*",
                        "widgets:read",
                        0
                ),
                RoutePermissionCoverageGate.permissionRule(
                        "GET",
                        "/api/v1/**",
                        "everything:read",
                        1
                ),
                RoutePermissionCoverageGate.permissionRule(
                        "GET",
                        "/api/v1/widgets/*",
                        "widgets:read-again",
                        2
                ),
                RoutePermissionCoverageGate.permissionRule(
                        "DELETE",
                        "/api/v1/legacy/*",
                        "legacy:delete",
                        3
                )
        );
        Set<RouteKey> publicRoutes = Set.of(
                RoutePermissionCoverageGate.routeKey("POST", "/api/v1/public-entry")
        );

        List<String> violations = RoutePermissionCoverageGate.validate(
                controllerRoutes,
                permissionRules,
                publicRoutes
        );

        assertThat(violations)
                .hasSize(8)
                .anyMatch(violation -> violation.startsWith("Controller 路由重复: POST /api/v1/widgets"))
                .anyMatch(violation -> violation.startsWith("缺少显式权限规则: POST /api/v1/widgets"))
                .anyMatch(violation -> violation.startsWith("权限规则重复: GET /api/v1/widgets/*"))
                .anyMatch(violation -> violation.startsWith("运行时权限规则匹配数不是 1: GET /api/v1/widgets/*"))
                .anyMatch(violation -> violation.startsWith("权限规则未精确对应 Controller: GET /api/v1/**"))
                .anyMatch(violation -> violation.startsWith("权限规则未精确对应 Controller: DELETE /api/v1/legacy/*"))
                .anyMatch(violation -> violation.startsWith("公开路由不存在或重复: POST /api/v1/public-entry"))
                .anyMatch(violation -> violation.startsWith("公开路径暴露了未允许的 HTTP 方法: GET /api/v1/public-entry"));
    }
}

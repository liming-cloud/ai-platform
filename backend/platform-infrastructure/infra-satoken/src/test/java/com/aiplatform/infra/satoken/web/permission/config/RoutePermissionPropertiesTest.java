package com.aiplatform.infra.satoken.web.permission.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.satoken.web.permission.model.RoutePermissionRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutePermissionPropertiesTest {
    @Test
    void rejectsMissingOrEmptyRoutePermissionConfiguration() {
        assertThatThrownBy(() -> new RoutePermissionProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("路由权限配置不能为空");
        assertThatThrownBy(() -> new RoutePermissionProperties(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("路由权限配置不能为空");
    }

    @Test
    void rejectsNullRoutePermissionRule() {
        List<RoutePermissionRule> routePermissions = new ArrayList<>();
        routePermissions.add(null);

        assertThatThrownBy(() -> new RoutePermissionProperties(routePermissions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("路由权限规则不能为空");
    }

    @Test
    void rejectsDuplicateRoutePermissionRule() {
        assertThatThrownBy(() -> new RoutePermissionProperties(List.of(
                        new RoutePermissionRule("GET", "/api/v1/model-providers", "model-hub:provider:read"),
                        new RoutePermissionRule("GET", "/api/v1/model-providers", "model-hub:provider:create")
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("路由权限规则重复：GET /api/v1/model-providers");
    }
}

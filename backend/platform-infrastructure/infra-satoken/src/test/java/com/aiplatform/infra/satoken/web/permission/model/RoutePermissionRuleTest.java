package com.aiplatform.infra.satoken.web.permission.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RoutePermissionRuleTest {
    @Test
    void normalizesRoutePermissionFactsBeforeMatching() {
        RoutePermissionRule rule = new RoutePermissionRule(
                " get ",
                " /api/v1/model-providers ",
                " model-hub:provider:read "
        );

        assertThat(rule.method()).isEqualTo("GET");
        assertThat(rule.pattern()).isEqualTo("/api/v1/model-providers");
        assertThat(rule.permission()).isEqualTo("model-hub:provider:read");
    }

    @Test
    void rejectsBlankRoutePermissionFacts() {
        assertThatThrownBy(() -> new RoutePermissionRule(" ", "/api/v1/model-providers", "model-hub:provider:read"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("HTTP 方法不能为空");
        assertThatThrownBy(() -> new RoutePermissionRule("GET", " ", "model-hub:provider:read"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("路由路径不能为空");
        assertThatThrownBy(() -> new RoutePermissionRule("GET", "/api/v1/model-providers", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("权限编码不能为空");
    }

    @Test
    void rejectsNullRoutePermissionFactsWithChineseBusinessMessages() {
        assertThatThrownBy(() -> new RoutePermissionRule(null, "/api/v1/model-providers", "model-hub:provider:read"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("HTTP 方法不能为空");
        assertThatThrownBy(() -> new RoutePermissionRule("GET", null, "model-hub:provider:read"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("路由路径不能为空");
        assertThatThrownBy(() -> new RoutePermissionRule("GET", "/api/v1/model-providers", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("权限编码不能为空");
    }

    @Test
    void rejectsUnsupportedRoutePermissionMethod() {
        assertThatThrownBy(() -> new RoutePermissionRule(
                "FETCH",
                "/api/v1/model-providers",
                "model-hub:provider:read"
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无效Method");
    }
}

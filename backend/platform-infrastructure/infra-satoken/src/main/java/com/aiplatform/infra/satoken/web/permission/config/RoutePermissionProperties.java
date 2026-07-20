package com.aiplatform.infra.satoken.web.permission.config;

import com.aiplatform.infra.satoken.web.permission.model.RoutePermissionRule;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 路由权限配置，权限码来自配置或权限目录数据，不写入业务 Controller。
 */
@ConfigurationProperties(prefix = "aip.security")
public record RoutePermissionProperties(List<RoutePermissionRule> routePermissions) {
    public RoutePermissionProperties {
        if (routePermissions == null || routePermissions.isEmpty()) {
            throw new IllegalArgumentException("路由权限配置不能为空");
        }
        Set<String> routeKeys = new HashSet<>();
        routePermissions.forEach(routePermission -> {
            if (routePermission == null) {
                throw new IllegalArgumentException("路由权限规则不能为空");
            }
            String routeKey = routePermission.method() + " " + routePermission.pattern();
            if (!routeKeys.add(routeKey)) {
                throw new IllegalArgumentException("路由权限规则重复：" + routeKey);
            }
        });
        routePermissions = List.copyOf(routePermissions);
    }
}

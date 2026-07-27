package com.aiplatform.foundation.platform.web.role;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 三个可执行应用的互斥角色配置。
 *
 * <p>该配置只负责启动边界校验，不参与业务权限判定。</p>
 */
@ConfigurationProperties("foundation")
public class ApplicationRoleProperties {

    private Role controlPlane = new Role();
    private Role query = new Role();
    private Role ingestion = new Role();

    public Role getControlPlane() {
        return controlPlane;
    }

    public void setControlPlane(Role controlPlane) {
        this.controlPlane = roleOrDefault(controlPlane);
    }

    public Role getQuery() {
        return query;
    }

    public void setQuery(Role query) {
        this.query = roleOrDefault(query);
    }

    public Role getIngestion() {
        return ingestion;
    }

    public void setIngestion(Role ingestion) {
        this.ingestion = roleOrDefault(ingestion);
    }

    public int enabledRoleCount() {
        return (controlPlane.enabled ? 1 : 0)
                + (query.enabled ? 1 : 0)
                + (ingestion.enabled ? 1 : 0);
    }

    private static Role roleOrDefault(Role role) {
        return role == null ? new Role() : role;
    }

    public static class Role {

        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

package com.aiplatform.shared.security.model;

import java.util.Set;

public final class CurrentUser {
    private final String userId;
    private final String username;
    private final String tenantId;
    private final Set<String> permissions;

    public CurrentUser(String userId, String username, String tenantId, Set<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.tenantId = tenantId;
        this.permissions = permissions;
    }

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    public String userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public String tenantId() {
        return tenantId;
    }

    public Set<String> permissions() {
        return permissions;
    }
}

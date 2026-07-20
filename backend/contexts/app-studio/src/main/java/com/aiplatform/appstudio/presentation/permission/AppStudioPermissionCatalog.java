package com.aiplatform.appstudio.presentation.permission;

import com.aiplatform.shared.security.model.PermissionCode;

/**
 * App Studio 权限目录，集中维护本上下文需要的路由与资源权限编码。
 */
public final class AppStudioPermissionCatalog {
    /** 读取 AI 应用权限。 */
    public static final PermissionCode APPLICATION_READ = new PermissionCode("app-studio:application:read");
    /** 创建 AI 应用权限。 */
    public static final PermissionCode APPLICATION_CREATE = new PermissionCode("app-studio:application:create");
    /** 更新 AI 应用权限。 */
    public static final PermissionCode APPLICATION_UPDATE = new PermissionCode("app-studio:application:update");
    /** 发布 AI 应用权限。 */
    public static final PermissionCode APPLICATION_PUBLISH = new PermissionCode("app-studio:application:publish");
    /** 使用 AI 应用权限。 */
    public static final PermissionCode APPLICATION_USE = new PermissionCode("app-studio:application:use");
    /** 维护 AI 应用资源授权权限。 */
    public static final PermissionCode APPLICATION_MANAGE_ACL = new PermissionCode("app-studio:application:manage-acl");
    /** 调用 AI 应用模型权限。 */
    public static final PermissionCode MODEL_USE = new PermissionCode("app-studio:model:use");
    /** 读取完整调试证据权限。 */
    public static final PermissionCode DEBUG_EVIDENCE_READ = new PermissionCode("app-studio:debug-evidence:read");

    private AppStudioPermissionCatalog() {
    }
}

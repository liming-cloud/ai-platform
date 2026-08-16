package com.aiplatform.foundation.security.identity;

/**
 * 平台治理会话使用的最小权限语言。
 */
public final class PlatformPermissions {

    /**
     * 允许主体建立平台范围会话；本身不授予任何平台工作区或租户资源操作权限。
     */
    public static final PermissionCode SESSION_ACCESS =
            new PermissionCode("platform-session:access");

    /**
     * 允许对作为业务资源目标的租户执行建立、激活、挂起和恢复。
     *
     * <p>该权限不包含任何租户正文、对象或审计内容读取能力。</p>
     */
    public static final PermissionCode TENANT_GOVERNANCE_WRITE =
            new PermissionCode("tenant-governance:write");

    /**
     * 允许管理全局人员账户、固定平台角色绑定及受控凭据重置。
     *
     * <p>它不授予租户正文、模型凭据或审计正文读取权限。</p>
     */
    public static final PermissionCode ACCOUNT_MANAGE =
            new PermissionCode("platform:accounts:manage");

    /**
     * 允许维护全局受控模型目录。
     *
     * <p>该权限不授予租户供应商连接、模型凭据、探测原始结果或业务正文读取能力。</p>
     */
    public static final PermissionCode MODEL_PLATFORM_WRITE =
            new PermissionCode("model-platform:write");

    /**
     * 允许读取跨租户脱敏运营汇总与租户级安全摘要。
     *
     * <p>该权限不授予租户正文、成员明细、问题、回答、引用或凭据读取能力。</p>
     */
    public static final PermissionCode OPERATIONS_READ =
            new PermissionCode("platform-operations:read");

    private PlatformPermissions() {
    }
}

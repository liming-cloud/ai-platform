package com.aiplatform.foundation.security.identity;

import java.util.Objects;
import java.util.Set;

/**
 * 仅由已验证平台会话恢复的不可变平台请求上下文。
 *
 * <p>本对象刻意不含租户、成员或目标资源。平台路由中的租户标识只能是受权生命周期操作的业务目标，
 * 不能借此变成登录状态或正文读取范围；会话准入权限也不等同于任何具体工作区权限。</p>
 */
public record PlatformRequestContext(
        SubjectId subjectId,
        CredentialType credentialType,
        AccessChannel channel,
        long permissionVersion,
        Set<PermissionCode> permissions,
        String traceId) {

    private static final int MAX_TRACE_ID_LENGTH = 128;

    public PlatformRequestContext {
        Objects.requireNonNull(subjectId, "主体标识不能为空");
        Objects.requireNonNull(credentialType, "凭据类型不能为空");
        Objects.requireNonNull(channel, "访问渠道不能为空");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "权限集合不能为空"));
        Objects.requireNonNull(traceId, "追踪标识不能为空");
        if (permissionVersion < 0L) {
            throw new IllegalArgumentException("权限版本不能为负数");
        }
        if (!permissions.contains(PlatformPermissions.SESSION_ACCESS)) {
            throw new IllegalArgumentException("平台上下文缺少会话准入权限");
        }
        if (traceId.isBlank()
                || traceId.length() > MAX_TRACE_ID_LENGTH
                || !traceId.equals(traceId.trim())) {
            throw new IllegalArgumentException("追踪标识格式不正确");
        }
        for (var index = 0; index < traceId.length(); index++) {
            if (Character.isWhitespace(traceId.charAt(index))
                    || Character.isISOControl(traceId.charAt(index))) {
                throw new IllegalArgumentException("追踪标识不能包含空白或控制字符");
            }
        }
    }

    /**
     * 判断平台会话是否持有指定权限。
     */
    public boolean hasPermission(PermissionCode permission) {
        return permissions.contains(Objects.requireNonNull(permission, "权限编码不能为空"));
    }
}

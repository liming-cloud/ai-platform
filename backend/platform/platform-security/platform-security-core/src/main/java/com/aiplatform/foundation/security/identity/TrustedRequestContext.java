package com.aiplatform.foundation.security.identity;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.Set;

/**
 * 认证适配器向应用层提供的不可变可信请求上下文。
 *
 * <p>该对象只能由服务端验证过的会话或凭据生成，不能直接映射调用方提交的租户字段。</p>
 */
public record TrustedRequestContext(
        TenantId tenantId,
        SubjectId subjectId,
        MembershipId membershipId,
        CredentialType credentialType,
        AccessChannel channel,
        long permissionVersion,
        Set<PermissionCode> permissions,
        String traceId) {

    private static final int MAX_TRACE_ID_LENGTH = 128;

    public TrustedRequestContext {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(subjectId, "主体标识不能为空");
        Objects.requireNonNull(membershipId, "成员标识不能为空");
        Objects.requireNonNull(credentialType, "凭据类型不能为空");
        Objects.requireNonNull(channel, "访问渠道不能为空");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "权限集合不能为空"));
        Objects.requireNonNull(traceId, "追踪标识不能为空");
        if (permissionVersion < 0) {
            throw new IllegalArgumentException("权限版本不能为负数");
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

    public boolean hasPermission(PermissionCode permission) {
        return permissions.contains(Objects.requireNonNull(permission, "权限编码不能为空"));
    }
}

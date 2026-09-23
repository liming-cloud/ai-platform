package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 绑定租户管理员资格版本的严格密码重置请求，不接受主体标识。 */
public final class TenantAdministratorPasswordResetRequest {

    private static final Set<String> FIELDS = Set.of("expectedVersion", "password");
    private final long expectedVersion;
    private final char[] password;

    private TenantAdministratorPasswordResetRequest(long expectedVersion, char[] password) {
        this.expectedVersion = expectedVersion;
        this.password = password;
    }

    /** 只接受非负整数版本和新口令，不接收权限或会话字段。 */
    public static TenantAdministratorPasswordResetRequest from(Map<String, Object> payload) {
        payload = Objects.requireNonNull(payload, "租户管理员密码请求不能为空");
        if (!payload.keySet().equals(FIELDS)
                || !(payload.get("expectedVersion") instanceof Number version)
                || !(version instanceof Byte || version instanceof Short
                        || version instanceof Integer || version instanceof Long)
                || version.longValue() < 0L
                || !(payload.get("password") instanceof String password)) {
            throw new IllegalArgumentException("租户管理员密码请求字段或版本无效");
        }
        return new TenantAdministratorPasswordResetRequest(version.longValue(), password.toCharArray());
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public char[] passwordCopy() {
        return password.clone();
    }

    /** 请求结束后清除 HTTP 层保留的新口令副本。 */
    public void clear() {
        Arrays.fill(password, '\0');
    }
}

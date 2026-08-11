package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 平台账户受控密码重置的严格 HTTP 请求；不接收主体、权限或会话字段。 */
public final class PlatformAccountPasswordResetRequest {

    private static final Set<String> FIELDS = Set.of("password");

    private final char[] password;

    private PlatformAccountPasswordResetRequest(char[] password) {
        this.password = password;
    }

    /**
     * 从严格 JSON 对象恢复一次性新口令。
     *
     * @throws IllegalArgumentException 当字段集合或字段类型不正确时拒绝。
     */
    public static PlatformAccountPasswordResetRequest from(Map<String, Object> payload) {
        payload = Objects.requireNonNull(payload, "平台账户密码重置请求不能为空");
        if (!payload.keySet().equals(FIELDS) || !(payload.get("password") instanceof String password)) {
            throw new IllegalArgumentException("平台账户密码重置请求必须且只能包含新口令");
        }
        return new PlatformAccountPasswordResetRequest(password.toCharArray());
    }

    public char[] passwordCopy() {
        return password.clone();
    }

    /** 请求结束后清除 HTTP 层保留的口令副本。 */
    public void clear() {
        Arrays.fill(password, '\0');
    }
}

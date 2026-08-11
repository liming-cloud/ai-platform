package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 建立平台账户的严格 HTTP 请求；不接收角色、权限、主体或会话字段。 */
public final class PlatformAccountEstablishmentRequest {

    private static final Set<String> FIELDS = Set.of("loginName", "password");

    private final String loginName;
    private final char[] password;

    private PlatformAccountEstablishmentRequest(String loginName, char[] password) {
        this.loginName = loginName;
        this.password = password;
    }

    /**
     * 从严格 JSON 对象恢复登录名和一次性初始口令。
     *
     * @throws IllegalArgumentException 当字段集合或字段类型不正确时拒绝。
     */
    public static PlatformAccountEstablishmentRequest from(Map<String, Object> payload) {
        payload = Objects.requireNonNull(payload, "平台账户建立请求不能为空");
        if (!payload.keySet().equals(FIELDS)
                || !(payload.get("loginName") instanceof String loginName)
                || !(payload.get("password") instanceof String password)) {
            throw new IllegalArgumentException("平台账户建立请求必须且只能包含登录名和初始口令");
        }
        return new PlatformAccountEstablishmentRequest(loginName, password.toCharArray());
    }

    public String loginName() {
        return loginName;
    }

    public char[] passwordCopy() {
        return password.clone();
    }

    /** 请求结束后清除 HTTP 层保留的口令副本。 */
    public void clear() {
        Arrays.fill(password, '\0');
    }
}

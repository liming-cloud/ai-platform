package com.aiplatform.foundation.organizationaccess.application.authentication.command;

import com.aiplatform.foundation.security.identity.AccessChannel;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 统一浏览器登录命令；租户编码只用于建立可选租户会话，不表示后续请求身份。
 */
public final class BrowserLoginCommand implements AutoCloseable {

    private static final Pattern TENANT_CODE = Pattern.compile("[a-z][a-z0-9-]{2,62}");
    private static final Pattern LOGIN_NAME = Pattern.compile("[a-z][a-z0-9._-]{2,63}");
    private static final int MAX_PASSWORD_LENGTH = 1024;

    private final String tenantCode;
    private final String loginName;
    private final char[] password;
    private final AccessChannel channel;
    private final String traceId;
    private boolean closed;

    public BrowserLoginCommand(
            String tenantCode,
            String loginName,
            char[] password,
            AccessChannel channel,
            String traceId) {
        this.tenantCode = normalizeTenantCode(tenantCode);
        this.loginName = normalizeLoginName(loginName);
        Objects.requireNonNull(password, "密码不能为空");
        if (password.length == 0 || password.length > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("密码长度不正确");
        }
        this.password = password.clone();
        this.channel = Objects.requireNonNull(channel, "访问渠道不能为空");
        this.traceId = requireTraceId(traceId);
    }

    public String tenantCode() {
        return tenantCode;
    }

    public String loginName() {
        return loginName;
    }

    public AccessChannel channel() {
        return channel;
    }

    public String traceId() {
        return traceId;
    }

    /**
     * 返回供单次验证使用的口令副本，由调用方负责清除。
     */
    public char[] copyPassword() {
        if (closed) {
            throw new IllegalStateException("浏览器登录命令已关闭");
        }
        return password.clone();
    }

    @Override
    public void close() {
        Arrays.fill(password, '\0');
        closed = true;
    }

    @Override
    public String toString() {
        return "BrowserLoginCommand[tenantCode=" + tenantCode
                + ", loginName=" + loginName
                + ", password=[已脱敏], channel=" + channel
                + ", traceId=" + traceId + "]";
    }

    private static String normalizeTenantCode(String value) {
        if (value == null) {
            return null;
        }
        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException("租户编码格式不正确");
        }
        var normalized = value.toLowerCase(Locale.ROOT);
        if (!TENANT_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("租户编码格式不正确");
        }
        return normalized;
    }

    private static String normalizeLoginName(String value) {
        Objects.requireNonNull(value, "登录名不能为空");
        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException("登录名格式不正确");
        }
        var normalized = value.toLowerCase(Locale.ROOT);
        if (!LOGIN_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("登录名格式不正确");
        }
        return normalized;
    }

    private static String requireTraceId(String value) {
        Objects.requireNonNull(value, "追踪标识不能为空");
        if (value.isBlank() || value.length() > 128 || !value.equals(value.trim())) {
            throw new IllegalArgumentException("追踪标识格式不正确");
        }
        return value;
    }
}

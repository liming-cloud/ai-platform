package com.aiplatform.identity.infrastructure.security.login;

import com.aiplatform.identity.domain.port.LoginAttemptDecision;
import com.aiplatform.identity.domain.port.LoginAttemptProtection;
import com.aiplatform.infra.redis.gateway.RedisStringGateway;
import com.aiplatform.infra.redis.policy.RedisKeyPolicy;
import com.aiplatform.shared.kernel.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Redis 固定窗口登录防护 adapter。任何存储异常都返回 UNAVAILABLE，由应用层统一失败关闭。
 */
@Component
public class RedisLoginAttemptProtection implements LoginAttemptProtection {
    private static final String KEY_PATTERN = "iam:{tenantId}:login-failures:{loginKey}";
    private static final String LOGIN_KEY_PLACEHOLDER = "loginKey";
    private static final String POLICY_USAGE = "登录失败固定窗口计数";

    private final RedisStringGateway gateway;
    private final LoginProtectionProperties properties;
    private final RedisKeyPolicy keyPolicy;

    public RedisLoginAttemptProtection(RedisStringGateway gateway, LoginProtectionProperties properties) {
        this.gateway = Objects.requireNonNull(gateway, "Redis 字符串网关不能为空");
        this.properties = Objects.requireNonNull(properties, "登录防护配置不能为空");
        this.keyPolicy = new RedisKeyPolicy(KEY_PATTERN, properties.getWindow(), POLICY_USAGE);
    }

    @Override
    public LoginAttemptDecision inspect(TenantId tenantId, String loginName) {
        String tenant = requireTenant(tenantId);
        String loginKey = loginKey(loginName);
        try {
            long failures = gateway.get(keyPolicy, tenant, LOGIN_KEY_PLACEHOLDER, loginKey)
                    .map(RedisLoginAttemptProtection::parseFailureCount)
                    .orElse(0L);
            return decision(failures);
        } catch (RuntimeException exception) {
            return LoginAttemptDecision.UNAVAILABLE;
        }
    }

    @Override
    public LoginAttemptDecision recordFailure(TenantId tenantId, String loginName) {
        String tenant = requireTenant(tenantId);
        String loginKey = loginKey(loginName);
        try {
            long failures = gateway.incrementWithinFixedWindow(
                    keyPolicy,
                    tenant,
                    LOGIN_KEY_PLACEHOLDER,
                    loginKey
            );
            return decision(failures);
        } catch (RuntimeException exception) {
            return LoginAttemptDecision.UNAVAILABLE;
        }
    }

    @Override
    public LoginAttemptDecision reset(TenantId tenantId, String loginName) {
        String tenant = requireTenant(tenantId);
        String loginKey = loginKey(loginName);
        try {
            gateway.delete(keyPolicy, tenant, LOGIN_KEY_PLACEHOLDER, loginKey);
            return LoginAttemptDecision.ALLOWED;
        } catch (RuntimeException exception) {
            return LoginAttemptDecision.UNAVAILABLE;
        }
    }

    private LoginAttemptDecision decision(long failures) {
        if (failures < 0) {
            throw new IllegalStateException("登录失败计数不能为负数");
        }
        return failures >= properties.getMaxFailures()
                ? LoginAttemptDecision.RATE_LIMITED
                : LoginAttemptDecision.ALLOWED;
    }

    private static long parseFailureCount(String value) {
        return Long.parseLong(value);
    }

    private static String requireTenant(TenantId tenantId) {
        return Objects.requireNonNull(tenantId, "租户 ID 不能为空").value();
    }

    private static String loginKey(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            throw new IllegalArgumentException("登录名不能为空");
        }
        String normalized = loginName.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 算法不可用", exception);
        }
    }
}

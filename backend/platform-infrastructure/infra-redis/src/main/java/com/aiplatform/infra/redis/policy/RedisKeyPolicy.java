package com.aiplatform.infra.redis.policy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis key 规范定义，集中约束 key 模板、TTL 上限和业务用途说明。
 */
public record RedisKeyPolicy(String keyPattern, Duration ttl, String usage) {
    private static final Duration MAX_TTL = Duration.ofDays(30);
    private static final int MAX_KEY_BYTES = 100;

    public RedisKeyPolicy(String keyPattern, Duration ttl, String usage) {
        String normalizedKeyPattern = requireText(keyPattern, "Redis key 模板");
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Redis key TTL 必须大于 0");
        }
        if (ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("Redis key TTL 不能超过 30 天");
        }
        this.keyPattern = normalizedKeyPattern;
        this.ttl = ttl;
        this.usage = requireText(usage, "Redis key 用途");
    }

    public String key(String tenantId, String placeholder, String value) {
        String normalizedPlaceholder = requireText(placeholder, "placeholder");
        String normalizedValue = requireText(value, "value");
        return key(tenantId, Map.of(normalizedPlaceholder, normalizedValue));
    }

    public String key(String tenantId, Map<String, String> variables) {
        String normalizedTenantId = requireText(tenantId, "tenantId");
        if (variables == null) {
            throw new IllegalArgumentException("Redis key 占位符变量不能为空");
        }
        String key = keyPattern.replace("{tenantId}", normalizedTenantId);
        for (Map.Entry<String, String> entry : normalizeVariables(variables).entrySet()) {
            key = key.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        if (key.contains("{") || key.contains("}")) {
            throw new IllegalArgumentException("Redis key 存在未替换占位符");
        }
        int keyBytes = key.getBytes(StandardCharsets.UTF_8).length;
        if (keyBytes > MAX_KEY_BYTES) {
            throw new IllegalArgumentException("Redis key 长度不能超过 100 字节");
        }
        return key;
    }

    /**
     * 为 TTL 增加基于业务 key 的稳定 10% 抖动，并保留 30 天上限，避免同类缓存集中在同一时间过期。
     */
    public Duration ttlWithJitter(String businessKey) {
        String normalizedBusinessKey = requireText(businessKey, "businessKey");
        long baseMillis = ttl.toMillis();
        long jitterBound = Math.max(1L, baseMillis / 10L);
        long jitterMillis = Math.floorMod(normalizedBusinessKey.hashCode(), jitterBound);
        Duration jittered = ttl.plusMillis(jitterMillis);
        return jittered.compareTo(MAX_TTL) > 0 ? MAX_TTL : jittered;
    }

    private static Map<String, String> normalizeVariables(Map<String, String> variables) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = requireText(entry.getKey(), "placeholder");
            String value = requireText(entry.getValue(), placeholder);
            if (normalized.putIfAbsent(placeholder, value) != null) {
                throw new IllegalArgumentException("Redis key 占位符重复：" + placeholder);
            }
        }
        return Map.copyOf(normalized);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }
}

package com.aiplatform.infra.redis.gateway;

import com.aiplatform.infra.redis.policy.RedisKeyPolicy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 字符串访问网关，统一执行 key 规范、TTL 抖动和写入大小限制。
 */
@Component
public class RedisStringGateway {
    private static final DefaultRedisScript<Long> FIXED_WINDOW_INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisStringGateway(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "Redis 模板不能为空");
    }

    public void set(RedisKeyPolicy policy, String tenantId, String placeholder, String businessKey, String value) {
        requireValue(value);
        RedisKeyPolicy redisKeyPolicy = requirePolicy(policy);
        redisTemplate.opsForValue().set(
                redisKeyPolicy.key(tenantId, placeholder, businessKey),
                value,
                redisKeyPolicy.ttlWithJitter(businessKey)
        );
    }

    public void set(RedisKeyPolicy policy, String tenantId, Map<String, String> variables, String businessKey, String value) {
        requireValue(value);
        RedisKeyPolicy redisKeyPolicy = requirePolicy(policy);
        redisTemplate.opsForValue().set(
                redisKeyPolicy.key(tenantId, variables),
                value,
                redisKeyPolicy.ttlWithJitter(businessKey)
        );
    }

    public boolean setIfAbsent(RedisKeyPolicy policy, String tenantId, String placeholder, String businessKey, String value) {
        requireValue(value);
        RedisKeyPolicy redisKeyPolicy = requirePolicy(policy);
        Boolean result = redisTemplate.opsForValue().setIfAbsent(
                redisKeyPolicy.key(tenantId, placeholder, businessKey),
                value,
                redisKeyPolicy.ttlWithJitter(businessKey)
        );
        return Boolean.TRUE.equals(result);
    }

    public boolean setIfAbsent(RedisKeyPolicy policy, String tenantId, Map<String, String> variables, String businessKey, String value) {
        requireValue(value);
        RedisKeyPolicy redisKeyPolicy = requirePolicy(policy);
        Boolean result = redisTemplate.opsForValue().setIfAbsent(
                redisKeyPolicy.key(tenantId, variables),
                value,
                redisKeyPolicy.ttlWithJitter(businessKey)
        );
        return Boolean.TRUE.equals(result);
    }

    /**
     * 原子递增固定窗口计数器，仅在首次写入时设置精确 TTL，适用于安全限流而非缓存抖动。
     */
    public long incrementWithinFixedWindow(
            RedisKeyPolicy policy,
            String tenantId,
            String placeholder,
            String businessKey
    ) {
        RedisKeyPolicy redisKeyPolicy = requirePolicy(policy);
        long ttlMillis = redisKeyPolicy.ttl().toMillis();
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("固定窗口 TTL 必须至少为 1 毫秒");
        }
        Long result = redisTemplate.execute(
                FIXED_WINDOW_INCREMENT_SCRIPT,
                List.of(redisKeyPolicy.key(tenantId, placeholder, businessKey)),
                Long.toString(ttlMillis)
        );
        if (result == null || result <= 0) {
            throw new IllegalStateException("Redis 固定窗口计数结果无效");
        }
        return result;
    }

    public Optional<String> get(RedisKeyPolicy policy, String tenantId, String placeholder, String businessKey) {
        RedisKeyPolicy redisKeyPolicy = requirePolicy(policy);
        return Optional.ofNullable(redisTemplate.opsForValue().get(redisKeyPolicy.key(tenantId, placeholder, businessKey)));
    }

    public Optional<String> get(RedisKeyPolicy policy, String tenantId, Map<String, String> variables) {
        RedisKeyPolicy redisKeyPolicy = requirePolicy(policy);
        return Optional.ofNullable(redisTemplate.opsForValue().get(redisKeyPolicy.key(tenantId, variables)));
    }

    public void delete(RedisKeyPolicy policy, String tenantId, String placeholder, String businessKey) {
        RedisKeyPolicy redisKeyPolicy = requirePolicy(policy);
        redisTemplate.delete(redisKeyPolicy.key(tenantId, placeholder, businessKey));
    }

    public void delete(RedisKeyPolicy policy, String tenantId, Map<String, String> variables) {
        RedisKeyPolicy redisKeyPolicy = requirePolicy(policy);
        redisTemplate.delete(redisKeyPolicy.key(tenantId, variables));
    }

    private static RedisKeyPolicy requirePolicy(RedisKeyPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Redis key 策略不能为空");
        }
        return policy;
    }

    private static void requireValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Redis value 不能为空");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
            throw new IllegalArgumentException("Redis value 不能超过 1MB");
        }
    }
}

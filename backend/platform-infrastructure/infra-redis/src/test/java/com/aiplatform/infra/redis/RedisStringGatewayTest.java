package com.aiplatform.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.redis.gateway.RedisStringGateway;
import com.aiplatform.infra.redis.policy.RedisKeyPolicy;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@SuppressWarnings("unchecked")
class RedisStringGatewayTest {
    @Test
    void writesStringValueWithRegisteredTtl() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisStringGateway gateway = new RedisStringGateway(redisTemplate);
        RedisKeyPolicy policy = new RedisKeyPolicy("common:{tenantId}:idem:{businessKey}", Duration.ofHours(24), "幂等");

        gateway.set(policy, " 2001 ", " businessKey ", " job-8101 ", "DONE");

        verify(valueOperations).set("common:2001:idem:job-8101", "DONE", policy.ttlWithJitter("job-8101"));
    }

    @Test
    void setIfAbsentUsesStringRedisTemplateAndTtl() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisStringGateway gateway = new RedisStringGateway(redisTemplate);
        RedisKeyPolicy policy = new RedisKeyPolicy("common:{tenantId}:idem:{businessKey}", Duration.ofHours(24), "幂等");
        when(valueOperations.setIfAbsent("common:2001:idem:job-8101", "RUNNING", policy.ttlWithJitter("job-8101"))).thenReturn(Boolean.TRUE);

        boolean acquired = gateway.setIfAbsent(policy, "2001", "businessKey", "job-8101", "RUNNING");

        assertThat(acquired).isTrue();
    }

    @Test
    void incrementsFixedWindowCounterAtomicallyWithExactTtl() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        RedisStringGateway gateway = new RedisStringGateway(redisTemplate);
        RedisKeyPolicy policy = new RedisKeyPolicy(
                "iam:{tenantId}:login-failures:{loginKey}",
                Duration.ofMinutes(15),
                "登录失败固定窗口计数"
        );
        String key = "iam:2001:login-failures:login-hash";
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(key)),
                eq("900000")
        )).thenReturn(3L);

        long failures = gateway.incrementWithinFixedWindow(
                policy,
                "2001",
                "loginKey",
                "login-hash"
        );

        assertThat(failures).isEqualTo(3L);
        org.mockito.ArgumentCaptor<RedisScript<Long>> scriptCaptor =
                org.mockito.ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of(key)), eq("900000"));
        assertThat(scriptCaptor.getValue().getScriptAsString())
                .contains("INCR")
                .contains("PEXPIRE")
                .contains("count == 1");
    }

    @Test
    void rejectsDamagedGatewayFactsWithChineseBusinessMessages() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        RedisStringGateway gateway = new RedisStringGateway(redisTemplate);
        RedisKeyPolicy policy = new RedisKeyPolicy("common:{tenantId}:idem:{businessKey}", Duration.ofHours(24), "幂等");

        assertThatThrownBy(() -> gateway.set(null, "2001", "businessKey", "job-8101", "DONE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Redis key 策略不能为空");
        assertThatThrownBy(() -> gateway.set(policy, "2001", "businessKey", "job-8101", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Redis value 不能为空");
        assertThatThrownBy(() -> gateway.set(policy, "2001", (String) null, "job-8101", "DONE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("placeholder不能为空");
        assertThatThrownBy(() -> gateway.set(policy, "2001", "businessKey", null, "DONE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value不能为空");
        assertThatThrownBy(() -> gateway.get(policy, "2001", null, "job-8101"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("placeholder不能为空");
        assertThatThrownBy(() -> gateway.delete(policy, "2001", "businessKey", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value不能为空");
    }
}

package com.aiplatform.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.redis.policy.RedisKeyPolicy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedisKeyPolicyTest {
    @Test
    void buildsTenantScopedKeyWithRegisteredPatternAndLengthLimit() {
        RedisKeyPolicy policy = new RedisKeyPolicy(
                "kb:{tenantId}:progress:ingest:{jobId}",
                Duration.ofDays(7),
                "文档解析和向量化进度短态"
        );

        String key = policy.key("2001", "jobId", "8101");

        assertThat(key).isEqualTo("kb:2001:progress:ingest:8101");
        assertThat(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(100);
    }

    @Test
    void buildsKeyWithMultipleBusinessPlaceholders() {
        RedisKeyPolicy policy = new RedisKeyPolicy(
                "model:{tenantId}:rate:{modelId}:{minute}",
                Duration.ofMinutes(2),
                "模型调用分钟级限流"
        );

        String key = policy.key("2001", Map.of("modelId", "glm-4", "minute", "202605222110"));

        assertThat(key).isEqualTo("model:2001:rate:glm-4:202605222110");
    }

    @Test
    void normalizesKeyPatternUsageAndVariablesBeforeBuildingKey() {
        RedisKeyPolicy policy = new RedisKeyPolicy(
                " common:{tenantId}:idem:{businessKey} ",
                Duration.ofHours(24),
                " 幂等 "
        );

        String key = policy.key(" 2001 ", " businessKey ", " job-8101 ");

        assertThat(policy.keyPattern()).isEqualTo("common:{tenantId}:idem:{businessKey}");
        assertThat(policy.usage()).isEqualTo("幂等");
        assertThat(key).isEqualTo("common:2001:idem:job-8101");
    }

    @Test
    void rejectsDuplicatePlaceholdersAfterNormalization() {
        RedisKeyPolicy policy = new RedisKeyPolicy(
                "common:{tenantId}:idem:{businessKey}",
                Duration.ofHours(24),
                "幂等"
        );

        assertThatThrownBy(() -> policy.key("2001", Map.of("businessKey", "job-8101", " businessKey ", "job-8102")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Redis key 占位符重复：businessKey");
    }

    @Test
    void rejectsKeysWithoutPositiveTtlOrLongerThanThirtyDays() {
        assertThatThrownBy(() -> new RedisKeyPolicy("iam:{tenantId}:cnt:loginFail:{username}", Duration.ZERO, "登录失败计数"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL 必须大于 0");

        assertThatThrownBy(() -> new RedisKeyPolicy("iam:{tenantId}:cnt:loginFail:{username}", Duration.ofDays(31), "登录失败计数"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL 不能超过 30 天");
    }

    @Test
    void rejectsDamagedKeyFactsWithChineseBusinessMessages() {
        assertThatThrownBy(() -> new RedisKeyPolicy(null, Duration.ofHours(1), "用途"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Redis key 模板不能为空");
        assertThatThrownBy(() -> new RedisKeyPolicy("common:{tenantId}:idem:{businessKey}", Duration.ofHours(1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Redis key 用途不能为空");

        RedisKeyPolicy policy = new RedisKeyPolicy(
                "common:{tenantId}:idem:{businessKey}",
                Duration.ofHours(24),
                "幂等"
        );

        assertThatThrownBy(() -> policy.key(null, "businessKey", "job-8101"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId不能为空");
        assertThatThrownBy(() -> policy.key("2001", null, "job-8101"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("placeholder不能为空");
        assertThatThrownBy(() -> policy.key("2001", "businessKey", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value不能为空");
        assertThatThrownBy(() -> policy.key("2001", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Redis key 占位符变量不能为空");

        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("businessKey", null);
        assertThatThrownBy(() -> policy.key("2001", variables))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessKey不能为空");
    }

    @Test
    void addsDeterministicJitterWithoutExceedingThirtyDays() {
        RedisKeyPolicy policy = new RedisKeyPolicy(
                "app:{tenantId}:cache:published:{applicationId}",
                Duration.ofMinutes(10),
                "应用发布版本配置热点缓存"
        );

        Duration ttl = policy.ttlWithJitter("app-1001");

        assertThat(ttl).isGreaterThanOrEqualTo(Duration.ofMinutes(10));
        assertThat(ttl).isLessThan(Duration.ofMinutes(11));
        assertThat(policy.ttlWithJitter(" app-1001 ")).isEqualTo(ttl);
    }
}

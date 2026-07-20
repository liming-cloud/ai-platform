package com.aiplatform.identity.infrastructure.security.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.domain.port.LoginAttemptDecision;
import com.aiplatform.infra.redis.gateway.RedisStringGateway;
import com.aiplatform.infra.redis.policy.RedisKeyPolicy;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;

@SuppressWarnings("unchecked")
class RedisLoginAttemptProtectionTest {
    @Test
    void recordsFailuresAndLimitsAtConfiguredThreshold() {
        RedisStringGateway gateway = mock(RedisStringGateway.class);
        LoginProtectionProperties properties = properties(3, Duration.ofMinutes(15));
        RedisLoginAttemptProtection protection = new RedisLoginAttemptProtection(gateway, properties);
        when(gateway.incrementWithinFixedWindow(
                any(RedisKeyPolicy.class),
                eq("2001"),
                eq("loginKey"),
                anyString()
        )).thenReturn(2L, 3L);

        assertThat(protection.recordFailure(TenantId.of("2001"), "admin"))
                .isEqualTo(LoginAttemptDecision.ALLOWED);
        assertThat(protection.recordFailure(TenantId.of("2001"), "admin"))
                .isEqualTo(LoginAttemptDecision.RATE_LIMITED);

        ArgumentCaptor<RedisKeyPolicy> policyCaptor = ArgumentCaptor.forClass(RedisKeyPolicy.class);
        verify(gateway, times(2)).incrementWithinFixedWindow(
                policyCaptor.capture(),
                eq("2001"),
                eq("loginKey"),
                anyString()
        );
        assertThat(policyCaptor.getValue().ttl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(policyCaptor.getValue().keyPattern())
                .isEqualTo("iam:{tenantId}:login-failures:{loginKey}");
    }

    @Test
    void allowsAgainAfterFixedWindowKeyExpiresAndResetsOnSuccess() {
        RedisStringGateway gateway = mock(RedisStringGateway.class);
        RedisLoginAttemptProtection protection = new RedisLoginAttemptProtection(
                gateway,
                properties(3, Duration.ofMinutes(15))
        );
        when(gateway.get(
                any(RedisKeyPolicy.class),
                eq("2001"),
                eq("loginKey"),
                anyString()
        )).thenReturn(Optional.of("3"), Optional.empty());

        assertThat(protection.inspect(TenantId.of("2001"), "admin"))
                .isEqualTo(LoginAttemptDecision.RATE_LIMITED);
        assertThat(protection.inspect(TenantId.of("2001"), "admin"))
                .isEqualTo(LoginAttemptDecision.ALLOWED);
        assertThat(protection.reset(TenantId.of("2001"), "admin"))
                .isEqualTo(LoginAttemptDecision.ALLOWED);

        verify(gateway).delete(
                any(RedisKeyPolicy.class),
                eq("2001"),
                eq("loginKey"),
                anyString()
        );
    }

    @Test
    void normalizesAndHashesLoginNameBeforeBuildingRedisKey() {
        RedisStringGateway gateway = mock(RedisStringGateway.class);
        RedisLoginAttemptProtection protection = new RedisLoginAttemptProtection(
                gateway,
                properties(3, Duration.ofMinutes(15))
        );
        when(gateway.get(
                any(RedisKeyPolicy.class),
                eq("2001"),
                eq("loginKey"),
                anyString()
        )).thenReturn(Optional.empty());

        protection.inspect(TenantId.of(" 2001 "), " Admin ");
        protection.inspect(TenantId.of("2001"), "ADMIN");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(2)).get(
                any(RedisKeyPolicy.class),
                eq("2001"),
                eq("loginKey"),
                keyCaptor.capture()
        );
        assertThat(keyCaptor.getAllValues())
                .hasSize(2)
                .allMatch(value -> value.length() == 64)
                .doesNotContain("admin")
                .containsOnly(keyCaptor.getAllValues().getFirst());
    }

    @Test
    void returnsUnavailableForRedisReadIncrementResetAndCorruptCounterFailures() {
        RedisStringGateway gateway = mock(RedisStringGateway.class);
        RedisLoginAttemptProtection protection = new RedisLoginAttemptProtection(
                gateway,
                properties(3, Duration.ofMinutes(15))
        );
        RedisConnectionFailureException failure = new RedisConnectionFailureException("redis down");
        when(gateway.get(any(RedisKeyPolicy.class), eq("2001"), eq("loginKey"), anyString()))
                .thenThrow(failure);
        when(gateway.incrementWithinFixedWindow(
                any(RedisKeyPolicy.class),
                eq("2001"),
                eq("loginKey"),
                anyString()
        )).thenThrow(failure);
        doThrow(failure).when(gateway).delete(
                any(RedisKeyPolicy.class),
                eq("2001"),
                eq("loginKey"),
                anyString()
        );

        assertThat(protection.inspect(TenantId.of("2001"), "admin"))
                .isEqualTo(LoginAttemptDecision.UNAVAILABLE);
        assertThat(protection.recordFailure(TenantId.of("2001"), "admin"))
                .isEqualTo(LoginAttemptDecision.UNAVAILABLE);
        assertThat(protection.reset(TenantId.of("2001"), "admin"))
                .isEqualTo(LoginAttemptDecision.UNAVAILABLE);

        RedisStringGateway corruptGateway = mock(RedisStringGateway.class);
        when(corruptGateway.get(any(RedisKeyPolicy.class), eq("2001"), eq("loginKey"), anyString()))
                .thenReturn(Optional.of("not-a-counter"));
        RedisLoginAttemptProtection corruptProtection = new RedisLoginAttemptProtection(
                corruptGateway,
                properties(3, Duration.ofMinutes(15))
        );
        assertThat(corruptProtection.inspect(TenantId.of("2001"), "admin"))
                .isEqualTo(LoginAttemptDecision.UNAVAILABLE);
    }

    private static LoginProtectionProperties properties(int maxFailures, Duration window) {
        LoginProtectionProperties properties = new LoginProtectionProperties();
        properties.setMaxFailures(maxFailures);
        properties.setWindow(window);
        return properties;
    }
}

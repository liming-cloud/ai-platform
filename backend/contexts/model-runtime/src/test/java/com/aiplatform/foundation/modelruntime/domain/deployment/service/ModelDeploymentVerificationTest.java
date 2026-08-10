package com.aiplatform.foundation.modelruntime.domain.deployment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityObservation;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityVerificationId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentStateException;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelDeploymentVerificationTest {

    private static final Instant NOW = Instant.parse("2026-08-10T02:00:00Z");

    @Test
    void 探测失败只形成失败事实且部署保持草稿() {
        var result = ModelDeploymentVerification.verify(
                draft(),
                new ModelCapabilityVerificationId(330000000000000001L),
                probe(false, Set.of(), "MODEL_CALL_FAILED"),
                NOW.plusSeconds(300));

        assertFalse(result.verified());
        assertFalse(result.verification().succeeded());
        assertEquals("MODEL_CALL_FAILED", result.verification().resultCode());
        assertEquals(ModelDeploymentStatus.DRAFT, result.revisedDeployment().status());
    }

    @Test
    void 实测能力不覆盖声明时不得验证() {
        var result = ModelDeploymentVerification.verify(
                draft(),
                new ModelCapabilityVerificationId(330000000000000001L),
                probe(true, Set.of(ModelCapability.CHAT), "MODEL_OK"),
                NOW.plusSeconds(300));

        assertFalse(result.verified());
        assertEquals("MODEL_CAPABILITY_MISMATCH", result.verification().resultCode());
        assertEquals(ModelDeploymentStatus.DRAFT, result.revisedDeployment().status());
    }

    @Test
    void 过期验证不得发布() {
        var verified = ModelDeploymentVerification.verify(
                draft(),
                new ModelCapabilityVerificationId(330000000000000001L),
                probe(true, Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT), "MODEL_OK"),
                NOW.plusSeconds(1));

        assertTrue(verified.verified());
        assertThrows(
                ModelDeploymentStateException.class,
                () -> verified.revisedDeployment().publish(NOW.plusSeconds(2)));
    }

    private static ModelDeployment draft() {
        return ModelDeployment.create(
                new TenantId(100000000000000001L),
                new ModelDeploymentId(320000000000000001L),
                new ProviderConnectionId(310000000000000001L),
                1,
                "deterministic-chat",
                "演示对话模型",
                ModelType.CHAT,
                Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT),
                8192,
                1024,
                null,
                NOW);
    }

    private static ModelCapabilityObservation probe(
            boolean reachable,
            Set<ModelCapability> capabilities,
            String resultCode) {
        return new ModelCapabilityObservation(
                reachable,
                capabilities,
                resultCode,
                Duration.ofMillis(10),
                NOW);
    }
}

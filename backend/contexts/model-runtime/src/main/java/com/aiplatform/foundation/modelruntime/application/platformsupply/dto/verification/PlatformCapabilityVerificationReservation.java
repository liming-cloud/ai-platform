package com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 第一条外呼之前冻结的整轮费用上界与精确验证绑定；不保存凭据或样本正文。 */
public record PlatformCapabilityVerificationReservation(long id, PlatformRequestContext actor,
        PlatformModelDeployment deployment, String targetFingerprint, String inputsDigest,
        String planFingerprint, String quoteDigest, Instant quoteExpiresAt, String sampleVersion,
        List<String> expectedSampleDigests, BigDecimal reservedFee, Instant startedAt, Instant deadline) {
    public PlatformCapabilityVerificationReservation {
        Objects.requireNonNull(actor); Objects.requireNonNull(deployment);
        for (var digest : List.of(targetFingerprint, inputsDigest, planFingerprint, quoteDigest))
            if (!digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("预留绑定摘要不正确");
        expectedSampleDigests = List.copyOf(expectedSampleDigests);
        if (id <= 0 || deployment.inputs() == null || expectedSampleDigests.size() != 3
                || expectedSampleDigests.stream().distinct().count() != 3
                || expectedSampleDigests.stream().anyMatch(value -> !value.matches("[0-9a-f]{64}"))
                || reservedFee == null || reservedFee.signum() < 0 || reservedFee.compareTo(new BigDecimal("0.03")) > 0
                || quoteExpiresAt == null || startedAt == null || deadline == null || !deadline.isAfter(startedAt)
                || deadline.isAfter(startedAt.plusSeconds(30)) || sampleVersion == null || sampleVersion.isBlank())
            throw new IllegalArgumentException("预留必须绑定完整三样本、费用上界和整轮期限");
    }
}

package com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** 已独立核实的账户计价及输入计量材料；启动配置只承载非秘密事实，不推断免费或授权外呼。 */
public record PlatformCapabilityVerificationPrice(long connectionId, long connectionRevision, String modelCode,
        ModelType modelType, String residency, String evidenceDigest, Instant observedAt, Instant expiresAt,
        String currency, String billingUnit, BigDecimal inputPrice, BigDecimal outputPrice,
        long inputUnitsUpperBound, long inputTokensUpperBound) {
    /**
     * 限定可表达的计量合同；材料真实性及账户适用性仍需执行前独立核实。
     * @throws IllegalArgumentException 计价、精确来源或计量上界缺失。
     */
    public PlatformCapabilityVerificationPrice {
        Objects.requireNonNull(modelType, "报价类型不能为空");
        if (connectionId <= 0 || connectionRevision < 0 || modelCode == null || modelCode.isBlank()
                || residency == null || residency.isBlank() || evidenceDigest == null || !evidenceDigest.matches("[0-9a-f]{64}")
                || observedAt == null || expiresAt == null || !expiresAt.isAfter(observedAt) || !"CNY".equals(currency)
                || !("PER_MILLION_TOKENS".equals(billingUnit) || "PER_REQUEST".equals(billingUnit))
                || inputPrice == null || inputPrice.signum() < 0 || outputPrice == null || outputPrice.signum() < 0
                || inputUnitsUpperBound <= 0 || inputTokensUpperBound <= 0)
            throw new IllegalArgumentException("报价必须包含精确来源、有效证据和独立输入上界");
    }
}

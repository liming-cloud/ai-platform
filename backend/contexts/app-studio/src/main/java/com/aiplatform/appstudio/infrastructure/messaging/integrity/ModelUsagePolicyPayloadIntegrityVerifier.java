package com.aiplatform.appstudio.infrastructure.messaging.integrity;

import com.aiplatform.appstudio.infrastructure.messaging.model.ModelUsagePolicyPublishedPayload;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppModelUsagePolicyPayloadDigestMapper;
import com.aiplatform.shared.kernel.Sha256Digests;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 在写入消费 receipt 前验证完整策略载荷，拒绝伪造高修订号污染本地投影。
 */
@Component("appModelUsagePolicyPayloadIntegrityVerifier")
public class ModelUsagePolicyPayloadIntegrityVerifier {
    private final AppModelUsagePolicyPayloadDigestMapper digestMapper;
    private final JsonMapper objectMapper;

    public ModelUsagePolicyPayloadIntegrityVerifier(
            AppModelUsagePolicyPayloadDigestMapper digestMapper,
            JsonMapper objectMapper
    ) {
        this.digestMapper = Objects.requireNonNull(digestMapper, "模型策略摘要 Mapper 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    public void verify(String payloadJson) {
        String payload = requirePayload(payloadJson);
        ModelUsagePolicyPublishedPayload parsedPayload = parse(payload);
        String reportedDigest = Sha256Digests.requireLowercase(
                parsedPayload.policyDigest(),
                "模型策略摘要"
        );
        String canonicalDigest = Sha256Digests.requireLowercase(
                digestMapper.canonicalDigest(payload),
                "模型策略 canonical 摘要"
        );
        if (!Sha256Digests.constantTimeEquals(reportedDigest, canonicalDigest)) {
            throw new IllegalArgumentException("模型策略载荷完整性校验失败");
        }
        if (Long.valueOf(Long.MAX_VALUE).equals(parsedPayload.policyRevision())) {
            throw new IllegalArgumentException("模型策略载荷完整性校验失败：修订号达到保留上限");
        }
    }

    private ModelUsagePolicyPublishedPayload parse(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, ModelUsagePolicyPublishedPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("模型策略事件载荷解析失败", exception);
        }
    }

    private static String requirePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("模型策略事件载荷不能为空");
        }
        return payloadJson;
    }
}

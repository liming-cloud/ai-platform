package com.aiplatform.modelhub.domain;

import com.aiplatform.shared.kernel.ApprovalEvidenceEnvelope;
import com.aiplatform.shared.kernel.ModelApprovalRuntimeOptions;
import com.aiplatform.shared.kernel.ValueObject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Typed, secret-free model approval evidence. This is the only seam allowed to project runtime configuration for review.
 */
public record ModelApprovalEvidence(
        ApprovalEvidenceEnvelope envelope,
        String targetDigest
) implements ValueObject {
    private static final String SOURCE_CONTEXT = "model-hub";
    public ModelApprovalEvidence {
        envelope = Objects.requireNonNull(envelope, "模型审批证据不能为空");
        targetDigest = requireDigest(targetDigest, "模型审批目标摘要");
        Object target = envelope.facts().get("target");
        if (!(target instanceof Map<?, ?> targetFacts)) {
            throw new IllegalArgumentException("模型审批证据 target 必须是对象");
        }
        String expectedTargetDigest = ApprovalEvidenceEnvelope.digestFacts(Map.of("target", targetFacts));
        if (!constantTimeEquals(expectedTargetDigest, targetDigest)) {
            throw new IllegalArgumentException("模型审批目标摘要与 after 事实不一致");
        }
    }

    public static ModelApprovalEvidence forProviderCreation(
            ModelProvider provider,
            boolean credentialConfigured,
            String requesterId,
            Instant requestedAt
    ) {
        Objects.requireNonNull(provider, "模型供应商不能为空");
        Map<String, Object> after = providerTarget(provider, credentialConfigured);
        return create(
                provider.tenantId().value(),
                provider.approvalRequestId(),
                ModelConfigChange.APPROVAL_TYPE_MODEL_PROVIDER_CHANGE,
                "MODEL_PROVIDER_CREATE",
                ModelConfigChange.RESOURCE_TYPE_MODEL_PROVIDER,
                provider.providerId(),
                provider.providerId(),
                null,
                requesterId,
                requestedAt,
                "ACTIVE",
                null,
                after,
                after,
                new TreeSet<>(after.keySet())
        );
    }

    public static ModelApprovalEvidence forModelConfigCreation(
            ModelProvider provider,
            ModelConfig target,
            String requesterId,
            Instant requestedAt
    ) {
        validateProviderConfig(provider, target);
        Map<String, Object> after = modelTarget(provider, target, true);
        Map<String, Object> resourceTarget = configResourceTarget(target, true);
        return create(
                target.tenantId().value(),
                target.approvalRequestId(),
                ModelConfigChange.APPROVAL_TYPE_MODEL_CONFIG_CHANGE,
                "MODEL_CONFIG_CREATE",
                ModelConfigChange.RESOURCE_TYPE_MODEL_CONFIG,
                target.modelConfigId(),
                provider.providerId(),
                target.modelConfigId(),
                requesterId,
                requestedAt,
                "INACTIVE+NOT_TESTED",
                null,
                after,
                resourceTarget,
                new TreeSet<>(after.keySet())
        );
    }

    public static ModelApprovalEvidence forModelConfigUpdate(
            ModelProvider provider,
            ModelConfig before,
            ModelConfig target,
            String requesterId,
            Instant requestedAt
    ) {
        validateProviderConfig(provider, before);
        validateProviderConfig(provider, target);
        if (!before.modelConfigId().equals(target.modelConfigId())) {
            throw new IllegalArgumentException("模型配置审批 before/after ID 不一致");
        }
        Map<String, Object> beforeFacts = modelTarget(provider, before, false);
        Map<String, Object> afterFacts = modelTarget(provider, target, true);
        Map<String, Object> resourceTarget = configResourceTarget(target, true);
        return create(
                target.tenantId().value(),
                target.approvalRequestId(),
                ModelConfigChange.APPROVAL_TYPE_MODEL_CONFIG_CHANGE,
                "MODEL_CONFIG_UPDATE",
                ModelConfigChange.RESOURCE_TYPE_MODEL_CONFIG,
                target.modelConfigId(),
                provider.providerId(),
                target.modelConfigId(),
                requesterId,
                requestedAt,
                "INACTIVE+NOT_TESTED",
                beforeFacts,
                afterFacts,
                resourceTarget,
                changedFields(beforeFacts, afterFacts)
        );
    }

    public static String targetDigestForProvider(ModelProvider provider) {
        return ApprovalEvidenceEnvelope.digestFacts(Map.of(
                "target",
                providerTarget(provider, provider.secretRef() != null)
        ));
    }

    public static String targetDigestForModelConfig(ModelConfig config) {
        Objects.requireNonNull(config, "模型配置不能为空");
        return ApprovalEvidenceEnvelope.digestFacts(Map.of("target", configResourceTarget(config, true)));
    }

    private static ModelApprovalEvidence create(
            String tenantId,
            String requestId,
            String approvalType,
            String action,
            String resourceType,
            String resourceId,
            String providerId,
            String modelConfigId,
            String requesterId,
            Instant requestedAt,
            String expectedPostDecisionState,
            Map<String, Object> before,
            Map<String, Object> after,
            Map<String, Object> target,
            Set<String> changedFields
    ) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("tenantId", requireText(tenantId, "租户 ID"));
        facts.put("approvalRequestId", requireText(requestId, "审批请求 ID"));
        facts.put("approvalType", requireText(approvalType, "审批类型"));
        facts.put("action", requireText(action, "审批动作"));
        facts.put("sourceContext", SOURCE_CONTEXT);
        facts.put("resourceType", requireText(resourceType, "来源资源类型"));
        facts.put("resourceId", requireText(resourceId, "来源资源 ID"));
        facts.put("providerId", requireText(providerId, "模型供应商 ID"));
        facts.put("modelConfigId", normalizeOptional(modelConfigId));
        facts.put("requesterId", requireText(requesterId, "申请人 ID"));
        facts.put("requestedAt", Objects.requireNonNull(requestedAt, "审批请求时间不能为空")
                .truncatedTo(ChronoUnit.MICROS)
                .toString());
        facts.put("riskLevel", ModelConfigRiskLevel.HIGH_RISK.name());
        facts.put("expectedPostDecisionState", requireText(expectedPostDecisionState, "审批后预期状态"));
        facts.put("before", before);
        facts.put("after", Objects.requireNonNull(after, "审批 after 事实不能为空"));
        facts.put("target", Objects.requireNonNull(target, "审批 target 事实不能为空"));
        facts.put("changedFields", List.copyOf(changedFields));
        ApprovalEvidenceEnvelope envelope = ApprovalEvidenceEnvelope.create(facts);
        String targetDigest = ApprovalEvidenceEnvelope.digestFacts(Map.of("target", target));
        return new ModelApprovalEvidence(envelope, targetDigest);
    }

    private static Map<String, Object> providerTarget(ModelProvider provider, boolean credentialConfigured) {
        Objects.requireNonNull(provider, "模型供应商不能为空");
        boolean credentialRequired = ModelProviderAuthTypes.requiresCredentialMaterial(provider.authType());
        if (credentialRequired != credentialConfigured) {
            throw new IllegalArgumentException("模型供应商凭证配置事实与鉴权类型不一致");
        }
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("providerId", provider.providerId());
        facts.put("providerCode", provider.providerCode());
        facts.put("providerKind", ModelProviderKinds.canonicalize(provider.providerCode()));
        facts.put("providerName", provider.providerName());
        facts.put("endpoint", provider.endpoint());
        facts.put("authType", provider.authType());
        facts.put("credentialConfigured", credentialConfigured);
        facts.put("outboundMode", ModelProviderKinds.LOCAL_DETERMINISTIC.equals(
                ModelProviderKinds.canonicalize(provider.providerCode())) ? "LOCAL_NO_NETWORK" : "PUBLIC_HTTPS_443");
        facts.put("approvalRequiredForConfigChange", provider.approvalRequiredForConfigChange());
        facts.put("expectedStatus", ModelProviderStatus.ACTIVE.name());
        return facts;
    }

    private static Map<String, Object> modelTarget(
            ModelProvider provider,
            ModelConfig config,
            boolean expectedAfterDecision
    ) {
        Map<String, Object> providerContext = new LinkedHashMap<>();
        providerContext.put("providerId", provider.providerId());
        providerContext.put("providerCode", provider.providerCode());
        providerContext.put("providerKind", ModelProviderKinds.canonicalize(provider.providerCode()));
        providerContext.put("providerName", provider.providerName());
        providerContext.put("endpoint", provider.endpoint());

        Map<String, Object> facts = new LinkedHashMap<>(configResourceTarget(config, expectedAfterDecision));
        facts.put("provider", providerContext);
        return facts;
    }

    private static Map<String, Object> configResourceTarget(
            ModelConfig config,
            boolean expectedAfterDecision
    ) {
        Map<String, Object> runtimeOptions = safeRuntimeOptions(config.options().values());
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("modelConfigId", config.modelConfigId());
        facts.put("modelCode", config.modelCode());
        facts.put("modelName", config.modelName());
        facts.put("modelType", config.modelType().name());
        facts.put("contextWindow", config.contextWindow());
        facts.put("embeddingDimension", config.embeddingDimension());
        facts.put("inputPricePer1k", ModelPriceContract.canonicalDatabaseString(
                config.inputPricePer1k(), "输入单价"));
        facts.put("outputPricePer1k", ModelPriceContract.canonicalDatabaseString(
                config.outputPricePer1k(), "输出单价"));
        facts.put("runtimeOptions", runtimeOptions);
        facts.put("expectedStatus", expectedAfterDecision ? ModelConfigStatus.INACTIVE.name() : config.status().name());
        facts.put("connectivityStatus", expectedAfterDecision
                ? ModelConnectivityStatus.NOT_TESTED.name()
                : config.connectivityStatus().name());
        facts.put("connectivityCheckedAt", expectedAfterDecision || config.connectivityCheckedAt() == null
                ? null
                : config.connectivityCheckedAt().toString());
        facts.put("connectivityMessage", expectedAfterDecision ? "" : config.connectivityMessage());
        return facts;
    }

    private static Map<String, Object> safeRuntimeOptions(Map<String, Object> options) {
        return ModelApprovalRuntimeOptions.validateAndNormalize(options);
    }

    private static Set<String> changedFields(Map<String, Object> before, Map<String, Object> after) {
        Set<String> changed = new TreeSet<>();
        collectChangedFields("", before, after, changed);
        return changed;
    }

    private static void collectChangedFields(
            String prefix,
            Map<String, Object> before,
            Map<String, Object> after,
            Set<String> changed
    ) {
        Set<String> keys = new TreeSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object left = before.get(key);
            Object right = after.get(key);
            if (left instanceof Map<?, ?> leftMap && right instanceof Map<?, ?> rightMap) {
                collectChangedFields(path, stringMap(leftMap), stringMap(rightMap), changed);
            } else if (!Objects.equals(left, right)) {
                changed.add(path);
            }
        }
    }

    private static Map<String, Object> stringMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, child) -> result.put(String.valueOf(key), child));
        return result;
    }

    private static void validateProviderConfig(ModelProvider provider, ModelConfig config) {
        Objects.requireNonNull(provider, "模型供应商不能为空");
        Objects.requireNonNull(config, "模型配置不能为空");
        if (!provider.tenantId().equals(config.tenantId()) || !provider.providerId().equals(config.providerId())) {
            throw new IllegalArgumentException("模型配置审批证据的 tenant/provider 关联不一致");
        }
    }

    private static String requireDigest(String value, String fieldName) {
        String normalized = requireText(value, fieldName).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(fieldName + "必须是 64 位 SHA-256 十六进制值");
        }
        return normalized;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

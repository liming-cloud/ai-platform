package com.aiplatform.modelhub.application;

import com.aiplatform.modelhub.application.error.ModelHubErrorCodes;
import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelApprovalEvidence;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.port.ModelConfigRepository;
import com.aiplatform.modelhub.domain.port.ModelProviderRepository;
import com.aiplatform.modelhub.domain.port.ModelUsagePolicyPublisher;
import com.aiplatform.modelhub.domain.service.ModelConfigActivationPolicy;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模型中心审批决策应用服务，消费审批中心事件后只更新本上下文聚合状态。
 */
@Service
public class ModelApprovalDecisionApplicationService {
    private final ModelProviderRepository providers;
    private final ModelConfigRepository configs;
    private final ModelConfigActivationPolicy activationPolicy;
    private final ModelUsagePolicyPublisher usagePolicyPublisher;

    @Autowired
    public ModelApprovalDecisionApplicationService(
            ModelProviderRepository providers,
            ModelConfigRepository configs,
            ModelConfigActivationPolicy activationPolicy,
            ModelUsagePolicyPublisher usagePolicyPublisher
    ) {
        this.providers = Objects.requireNonNull(providers, "模型供应商仓储不能为空");
        this.configs = Objects.requireNonNull(configs, "模型配置仓储不能为空");
        this.activationPolicy = Objects.requireNonNull(activationPolicy, "模型配置启用策略不能为空");
        this.usagePolicyPublisher = Objects.requireNonNull(usagePolicyPublisher, "模型使用策略发布端口不能为空");
    }

    @Transactional
    public void applyProviderDecision(
            TenantId tenantId,
            String providerId,
            String approvalRequestId,
            String evidenceSchema,
            String evidenceDigest,
            String targetDigest,
            boolean approved,
            Instant occurredAt
    ) {
        TenantId normalizedTenantId = requireValue(tenantId, "租户 ID");
        String normalizedProviderId = requireText(providerId, "模型供应商 ID");
        String normalizedApprovalRequestId = requireText(approvalRequestId, "审批请求 ID");
        String normalizedEvidenceSchema = requireEvidenceSchema(evidenceSchema);
        String normalizedEvidenceDigest = requireDigest(evidenceDigest, "模型审批证据摘要");
        String normalizedTargetDigest = requireDigest(targetDigest, "模型审批目标摘要");
        Instant updatedAt = requireValue(occurredAt, "审批决策事件发生时间");
        ModelProvider provider = providers.findById(normalizedTenantId, normalizedProviderId)
                .orElseThrow(() -> new ApplicationException(
                        ModelHubErrorCodes.PROVIDER_NOT_FOUND,
                        "供应商 ID=" + normalizedProviderId
                ));
        if (provider.status() != ModelProviderStatus.PENDING_APPROVAL
                || !normalizedApprovalRequestId.equals(provider.approvalRequestId())) {
            return;
        }
        if (!normalizedEvidenceSchema.equals(provider.approvalEvidenceSchema())
                || !matchesDigest(normalizedEvidenceDigest, provider.approvalEvidenceDigest())
                || !matchesDigest(normalizedTargetDigest, provider.approvalTargetDigest())) {
            return;
        }
        if (!matchesTargetDigest(normalizedTargetDigest, providerTargetDigest(provider))) {
            return;
        }
        ModelProvider decidedProvider = approved ? provider.approve(updatedAt) : provider.reject(updatedAt);
        java.util.Optional<ModelProvider> persistedProvider = providers.compareAndSetApprovalDecision(
                decidedProvider,
                provider.version(),
                provider.status(),
                normalizedApprovalRequestId,
                normalizedEvidenceDigest,
                normalizedTargetDigest
        );
        if (persistedProvider.isEmpty()) {
            return;
        }
        ModelProvider updatedProvider = persistedProvider.orElseThrow();
        for (ModelConfig config : configs.findByProviderId(normalizedTenantId, normalizedProviderId)) {
            ModelConfig updatedConfig = approved
                    ? activationPolicy.activateWhenReady(updatedProvider, config, updatedAt)
                    : activationPolicy.inactivateWhenProviderRejected(config, updatedAt);
            ModelConfig savedConfig = updatedConfig.equals(config)
                    ? config
                    : configs.compareAndSet(updatedConfig, config.version(), config.status())
                            .orElseThrow(() -> new IllegalStateException(
                                    "模型供应商审批回写期间模型配置发生并发修改"
                            ));
            publishUsagePolicy(updatedProvider, savedConfig);
        }
    }

    @Transactional
    public void applyModelConfigDecision(
            TenantId tenantId,
            String modelConfigId,
            String approvalRequestId,
            String evidenceSchema,
            String evidenceDigest,
            String targetDigest,
            boolean approved,
            Instant occurredAt
    ) {
        TenantId normalizedTenantId = requireValue(tenantId, "租户 ID");
        String normalizedModelConfigId = requireText(modelConfigId, "模型配置 ID");
        String normalizedApprovalRequestId = requireText(approvalRequestId, "审批请求 ID");
        String normalizedEvidenceSchema = requireEvidenceSchema(evidenceSchema);
        String normalizedEvidenceDigest = requireDigest(evidenceDigest, "模型审批证据摘要");
        String normalizedTargetDigest = requireDigest(targetDigest, "模型审批目标摘要");
        Instant updatedAt = requireValue(occurredAt, "审批决策事件发生时间");
        ModelConfig config = configs.findByModelConfigId(normalizedTenantId, normalizedModelConfigId)
                .orElseThrow(() -> new ApplicationException(
                        ModelHubErrorCodes.MODEL_CONFIG_NOT_FOUND,
                        "模型配置 ID=" + normalizedModelConfigId
                ));
        if (config.status() != ModelConfigStatus.PENDING_APPROVAL
                || !normalizedApprovalRequestId.equals(config.approvalRequestId())) {
            return;
        }
        if (!normalizedEvidenceSchema.equals(config.approvalEvidenceSchema())
                || !matchesDigest(normalizedEvidenceDigest, config.approvalEvidenceDigest())
                || !matchesDigest(normalizedTargetDigest, config.approvalTargetDigest())) {
            return;
        }
        if (!matchesTargetDigest(
                normalizedTargetDigest,
                ModelApprovalEvidence.targetDigestForModelConfig(config)
        )) {
            return;
        }
        ModelConfig decidedConfig = approved ? config.approve(updatedAt) : config.reject(updatedAt);
        java.util.Optional<ModelConfig> persistedConfig = configs.compareAndSetApprovalDecision(
                decidedConfig,
                config.version(),
                config.status(),
                normalizedApprovalRequestId,
                normalizedEvidenceDigest,
                normalizedTargetDigest
        );
        if (persistedConfig.isEmpty()) {
            return;
        }
        ModelProvider provider = providers.findById(normalizedTenantId, config.providerId())
                .orElseThrow(() -> new ApplicationException(
                        ModelHubErrorCodes.PROVIDER_NOT_FOUND,
                        "供应商 ID=" + config.providerId()
                ));
        publishUsagePolicy(provider, persistedConfig.orElseThrow());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String requireDigest(String value, String fieldName) {
        String normalized = requireText(value, fieldName).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(fieldName + "必须是 64 位 SHA-256 十六进制值");
        }
        return normalized;
    }

    private static String requireEvidenceSchema(String value) {
        String schema = requireText(value, "模型审批证据 schema");
        if (!com.aiplatform.shared.kernel.ApprovalEvidenceEnvelope.MODEL_APPROVAL_SCHEMA_V1.equals(schema)) {
            throw new IllegalArgumentException("模型审批证据 schema 不受支持：" + schema);
        }
        return schema;
    }

    private static String providerTargetDigest(ModelProvider provider) {
        try {
            return ModelApprovalEvidence.targetDigestForProvider(provider);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return null;
        }
    }

    private static boolean matchesTargetDigest(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        );
    }

    private static boolean matchesDigest(String expected, String actual) {
        return actual != null && java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        );
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private void publishUsagePolicy(ModelProvider provider, ModelConfig config) {
        usagePolicyPublisher.publish(provider, config);
    }
}

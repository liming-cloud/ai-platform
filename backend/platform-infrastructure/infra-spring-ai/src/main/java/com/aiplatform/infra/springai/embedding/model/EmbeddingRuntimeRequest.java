package com.aiplatform.infra.springai.embedding.model;

import com.aiplatform.infra.springai.model.strategy.support.ModelOptionValues;
import com.aiplatform.shared.kernel.ModelRuntimeContracts;
import com.aiplatform.shared.kernel.Sha256Digests;
import java.util.Locale;
import java.util.Map;

/**
 * 受控向量模型运行时请求，来自业务上下文本地模型策略投影，不接收前端直传供应商参数。
 */
public record EmbeddingRuntimeRequest(
        String tenantId,
        String modelConfigId,
        String providerId,
        String providerCode,
        String providerKind,
        String providerEndpoint,
        String authType,
        String secretRefId,
        String modelCode,
        int modelConfigVersion,
        String modelType,
        Map<String, Object> effectiveOptions,
        String effectiveOptionsDigest,
        String policyVersion
) {
    private static final String AUTH_TYPE_API_KEY = "api_key";
    private static final String AUTH_TYPE_NO_AUTH = "no_auth";
    private static final String AUTH_TYPE_AWS_CREDENTIALS = "aws_credentials";
    private static final String AUTH_TYPE_GCP_SERVICE_ACCOUNT = "gcp_service_account";

    public EmbeddingRuntimeRequest {
        tenantId = ModelRuntimeContracts.requireText(tenantId, "租户");
        modelConfigId = ModelRuntimeContracts.requireModelConfigId(modelConfigId);
        providerId = ModelRuntimeContracts.requireText(providerId, "模型供应商 ID");
        providerCode = ModelRuntimeContracts.requireProviderCode(providerCode);
        providerKind = ModelRuntimeContracts.requireProviderKind(providerKind);
        providerEndpoint = ModelRuntimeContracts.requireText(providerEndpoint, "供应商访问地址");
        authType = normalizeAuthType(authType);
        if (requiresCredentialMaterial(authType)) {
            secretRefId = ModelRuntimeContracts.requireText(secretRefId, "模型供应商凭证材料引用 ID");
        } else if (AUTH_TYPE_NO_AUTH.equals(authType)) {
            rejectSecretRef(secretRefId);
            secretRefId = null;
        } else {
            throw new IllegalArgumentException("暂不支持模型供应商鉴权方式：" + authType);
        }
        modelCode = ModelRuntimeContracts.requireModelCode(modelCode);
        if (modelConfigVersion < 0) {
            throw new IllegalArgumentException("模型配置版本不能小于 0");
        }
        modelType = ModelRuntimeContracts.requireText(modelType, "模型类型").toUpperCase(Locale.ROOT);
        effectiveOptions = ModelRuntimeContracts.copyRequiredMap(effectiveOptions, "模型 options");
        effectiveOptionsDigest = Sha256Digests.requireLowercase(
                effectiveOptionsDigest,
                "模型 effective options 摘要"
        );
        policyVersion = ModelRuntimeContracts.requirePolicyVersion(policyVersion);
    }

    public Integer optionAsInteger(String key) {
        if (!effectiveOptions.containsKey(key)) {
            return null;
        }
        return ModelOptionValues.integerValue(effectiveOptions, key)
                .orElseThrow(() -> new IllegalArgumentException(key + " 必须是整数"));
    }

    public boolean requiresCredentialMaterial() {
        return requiresCredentialMaterial(authType);
    }

    private static String normalizeAuthType(String authType) {
        return ModelRuntimeContracts.requireText(authType, "供应商鉴权方式").toLowerCase(Locale.ROOT);
    }

    private static boolean requiresCredentialMaterial(String authType) {
        return AUTH_TYPE_API_KEY.equals(authType)
                || AUTH_TYPE_AWS_CREDENTIALS.equals(authType)
                || AUTH_TYPE_GCP_SERVICE_ACCOUNT.equals(authType);
    }

    private static void rejectSecretRef(String secretRefId) {
        if (secretRefId != null && !secretRefId.isBlank()) {
            throw new IllegalArgumentException("no_auth 模型供应商不允许携带凭证材料引用");
        }
    }
}

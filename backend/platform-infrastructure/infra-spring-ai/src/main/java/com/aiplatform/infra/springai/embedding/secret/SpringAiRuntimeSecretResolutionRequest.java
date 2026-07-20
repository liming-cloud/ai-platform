package com.aiplatform.infra.springai.embedding.secret;

import com.aiplatform.shared.kernel.ModelRuntimeContracts;
import com.aiplatform.shared.kernel.Sha256Digests;
import java.util.Locale;
import java.util.Map;

/**
 * 运行时凭证解析的完整 authority 请求。解析器必须把这些字段与 model-hub 当前状态逐一绑定，
 * 不能仅凭租户和 secret 引用释放凭证材料。
 */
public record SpringAiRuntimeSecretResolutionRequest(
        String tenantId,
        String modelConfigId,
        String providerId,
        String providerCode,
        String providerKind,
        String endpoint,
        String authType,
        String modelCode,
        int modelConfigVersion,
        String modelType,
        Map<String, Object> effectiveOptions,
        String effectiveOptionsDigest,
        String secretRefId
) {
    private static final String AUTH_TYPE_API_KEY = "api_key";
    private static final String AUTH_TYPE_NO_AUTH = "no_auth";
    private static final String AUTH_TYPE_AWS_CREDENTIALS = "aws_credentials";
    private static final String AUTH_TYPE_GCP_SERVICE_ACCOUNT = "gcp_service_account";

    public SpringAiRuntimeSecretResolutionRequest {
        tenantId = ModelRuntimeContracts.requireText(tenantId, "租户 ID");
        modelConfigId = ModelRuntimeContracts.requireModelConfigId(modelConfigId);
        providerId = ModelRuntimeContracts.requireText(providerId, "模型供应商 ID");
        providerCode = ModelRuntimeContracts.requireProviderCode(providerCode);
        providerKind = ModelRuntimeContracts.requireProviderKind(providerKind);
        endpoint = ModelRuntimeContracts.requireText(endpoint, "供应商访问地址");
        authType = normalizeAuthType(authType);
        modelCode = ModelRuntimeContracts.requireModelCode(modelCode);
        if (modelConfigVersion < 0) {
            throw new IllegalArgumentException("模型配置版本不能小于 0");
        }
        modelType = ModelRuntimeContracts.requireText(modelType, "模型类型").toUpperCase(Locale.ROOT);
        effectiveOptions = ModelRuntimeContracts.copyRequiredMap(effectiveOptions, "模型 effective options");
        effectiveOptionsDigest = Sha256Digests.requireLowercase(
                effectiveOptionsDigest,
                "模型 effective options 摘要"
        );
        if (requiresCredentialMaterial(authType)) {
            secretRefId = ModelRuntimeContracts.requireText(secretRefId, "模型供应商凭证材料引用 ID");
        } else if (AUTH_TYPE_NO_AUTH.equals(authType)) {
            if (secretRefId != null && !secretRefId.isBlank()) {
                throw new IllegalArgumentException("no_auth 模型供应商不允许携带凭证材料引用");
            }
            secretRefId = null;
        } else {
            throw new IllegalArgumentException("暂不支持模型供应商鉴权方式：" + authType);
        }
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
}

package com.aiplatform.knowledge.domain.port.modelpolicy.model;

import com.aiplatform.shared.kernel.ValueObject;
import java.util.Locale;

/**
 * 知识库本地模型运行时连接引用，不包含模型供应商明文凭证材料。
 */
public record KnowledgeModelRuntimeConnectionRef(
        String endpoint,
        String authType,
        String secretRefId,
        String secretMaskedValue
) implements ValueObject {
    private static final String AUTH_TYPE_API_KEY = "api_key";
    private static final String AUTH_TYPE_NO_AUTH = "no_auth";
    private static final String AUTH_TYPE_AWS_CREDENTIALS = "aws_credentials";
    private static final String AUTH_TYPE_GCP_SERVICE_ACCOUNT = "gcp_service_account";

    public KnowledgeModelRuntimeConnectionRef {
        endpoint = requireText(endpoint, "供应商访问地址");
        authType = normalizeAuthType(authType);
        if (requiresCredentialMaterial(authType)) {
            secretRefId = requireText(secretRefId, "模型供应商凭证材料引用 ID");
            secretMaskedValue = requireText(secretMaskedValue, "模型供应商凭证材料脱敏值");
        } else if (AUTH_TYPE_NO_AUTH.equals(authType)) {
            rejectSecretRef(secretRefId, secretMaskedValue);
            secretRefId = null;
            secretMaskedValue = null;
        } else {
            throw new IllegalArgumentException("暂不支持模型供应商鉴权方式：" + authType);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalizeAuthType(String authType) {
        return requireText(authType, "供应商鉴权方式").toLowerCase(Locale.ROOT);
    }

    private static boolean requiresCredentialMaterial(String authType) {
        return AUTH_TYPE_API_KEY.equals(authType)
                || AUTH_TYPE_AWS_CREDENTIALS.equals(authType)
                || AUTH_TYPE_GCP_SERVICE_ACCOUNT.equals(authType);
    }

    private static void rejectSecretRef(String secretRefId, String secretMaskedValue) {
        if (hasText(secretRefId) || hasText(secretMaskedValue)) {
            throw new IllegalArgumentException("no_auth 模型供应商不允许携带凭证材料引用");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

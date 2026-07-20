package com.aiplatform.modelhub.domain;

import java.util.Locale;

/**
 * 模型供应商认证类型，区分无凭证、API Key 和云厂商受控凭证材料。
 */
public final class ModelProviderAuthTypes {
    public static final String API_KEY = "api_key";
    public static final String NO_AUTH = "no_auth";
    public static final String AWS_CREDENTIALS = "aws_credentials";
    public static final String GCP_SERVICE_ACCOUNT = "gcp_service_account";

    private ModelProviderAuthTypes() {
    }

    public static String normalize(String authType) {
        if (authType == null || authType.isBlank()) {
            throw new IllegalArgumentException("供应商鉴权方式不能为空");
        }
        String normalized = authType.trim().toLowerCase(Locale.ROOT);
        if (!API_KEY.equals(normalized)
                && !NO_AUTH.equals(normalized)
                && !AWS_CREDENTIALS.equals(normalized)
                && !GCP_SERVICE_ACCOUNT.equals(normalized)) {
            throw new IllegalArgumentException("暂不支持模型供应商鉴权方式：" + authType);
        }
        return normalized;
    }

    public static boolean requiresCredentialMaterial(String authType) {
        String normalized = normalize(authType);
        return API_KEY.equals(normalized)
                || AWS_CREDENTIALS.equals(normalized)
                || GCP_SERVICE_ACCOUNT.equals(normalized);
    }

    public static boolean isNoAuth(String authType) {
        return NO_AUTH.equals(normalize(authType));
    }
}

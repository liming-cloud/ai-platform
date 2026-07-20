package com.aiplatform.modelhub.domain;

import java.util.Locale;
import java.util.Map;

/**
 * 模型供应商类型注册表，集中维护创建入口、模型 options 与运行时之间的 canonical family 和鉴权契约。
 */
public final class ModelProviderKinds {
    public static final String OPENAI_COMPATIBLE = "openai-compatible";
    public static final String AZURE_OPENAI = "azure-openai";
    public static final String OLLAMA = "ollama";
    public static final String MISTRAL_AI = "mistral-ai";
    public static final String BEDROCK = "bedrock";
    public static final String VERTEX_AI = "vertex-ai";
    public static final String LOCAL_DETERMINISTIC = "local-deterministic";

    private static final Map<String, ModelProviderCapability> CAPABILITIES = Map.ofEntries(
            Map.entry(OPENAI_COMPATIBLE, new ModelProviderCapability(OPENAI_COMPATIBLE, ModelProviderAuthTypes.API_KEY)),
            // 兼容已存在的 OpenAI 供应商编码；新入口统一使用 openai-compatible。
            Map.entry("openai", new ModelProviderCapability(OPENAI_COMPATIBLE, ModelProviderAuthTypes.API_KEY)),
            Map.entry(AZURE_OPENAI, new ModelProviderCapability(AZURE_OPENAI, ModelProviderAuthTypes.API_KEY)),
            Map.entry("azure_openai", new ModelProviderCapability(AZURE_OPENAI, ModelProviderAuthTypes.API_KEY)),
            Map.entry("azureopenai", new ModelProviderCapability(AZURE_OPENAI, ModelProviderAuthTypes.API_KEY)),
            Map.entry(OLLAMA, new ModelProviderCapability(OLLAMA, ModelProviderAuthTypes.NO_AUTH)),
            Map.entry(MISTRAL_AI, new ModelProviderCapability(MISTRAL_AI, ModelProviderAuthTypes.API_KEY)),
            Map.entry("mistral_ai", new ModelProviderCapability(MISTRAL_AI, ModelProviderAuthTypes.API_KEY)),
            Map.entry("mistral", new ModelProviderCapability(MISTRAL_AI, ModelProviderAuthTypes.API_KEY)),
            Map.entry(BEDROCK, new ModelProviderCapability(BEDROCK, ModelProviderAuthTypes.AWS_CREDENTIALS)),
            Map.entry("amazon-bedrock", new ModelProviderCapability(BEDROCK, ModelProviderAuthTypes.AWS_CREDENTIALS)),
            Map.entry("amazon_bedrock", new ModelProviderCapability(BEDROCK, ModelProviderAuthTypes.AWS_CREDENTIALS)),
            Map.entry("aws-bedrock", new ModelProviderCapability(BEDROCK, ModelProviderAuthTypes.AWS_CREDENTIALS)),
            Map.entry("aws_bedrock", new ModelProviderCapability(BEDROCK, ModelProviderAuthTypes.AWS_CREDENTIALS)),
            Map.entry(VERTEX_AI, new ModelProviderCapability(VERTEX_AI, ModelProviderAuthTypes.GCP_SERVICE_ACCOUNT)),
            Map.entry("vertex_ai", new ModelProviderCapability(VERTEX_AI, ModelProviderAuthTypes.GCP_SERVICE_ACCOUNT)),
            Map.entry("vertexai", new ModelProviderCapability(VERTEX_AI, ModelProviderAuthTypes.GCP_SERVICE_ACCOUNT)),
            Map.entry("google-vertex-ai", new ModelProviderCapability(VERTEX_AI, ModelProviderAuthTypes.GCP_SERVICE_ACCOUNT)),
            Map.entry("google_vertex_ai", new ModelProviderCapability(VERTEX_AI, ModelProviderAuthTypes.GCP_SERVICE_ACCOUNT)),
            Map.entry("gcp-vertex-ai", new ModelProviderCapability(VERTEX_AI, ModelProviderAuthTypes.GCP_SERVICE_ACCOUNT)),
            Map.entry("gcp_vertex_ai", new ModelProviderCapability(VERTEX_AI, ModelProviderAuthTypes.GCP_SERVICE_ACCOUNT)),
            Map.entry(LOCAL_DETERMINISTIC, new ModelProviderCapability(LOCAL_DETERMINISTIC, ModelProviderAuthTypes.NO_AUTH))
    );

    private ModelProviderKinds() {
    }

    public static String canonicalize(String providerKind) {
        return requireCapability(providerKind).canonicalKind();
    }

    public static String requiredAuthType(String providerKind) {
        return requireCapability(providerKind).requiredAuthType();
    }

    public static void requireCompatibleRuntimeKind(String providerKind, String runtimeProviderKind) {
        String canonicalProviderKind = canonicalize(providerKind);
        String canonicalRuntimeKind;
        try {
            canonicalRuntimeKind = canonicalize(runtimeProviderKind);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "模型 options.providerKind 暂不支持：" + requireText(runtimeProviderKind)
                            + "；必须与供应商类型 " + canonicalProviderKind + " 一致",
                    exception
            );
        }
        if (!canonicalProviderKind.equals(canonicalRuntimeKind)) {
            throw new IllegalArgumentException(
                    "模型 options.providerKind " + canonicalRuntimeKind
                            + " 必须与供应商类型 " + canonicalProviderKind + " 一致"
            );
        }
    }

    private static ModelProviderCapability requireCapability(String providerKind) {
        String normalized = requireText(providerKind).toLowerCase(Locale.ROOT);
        ModelProviderCapability capability = CAPABILITIES.get(normalized);
        if (capability == null) {
            throw new IllegalArgumentException("暂不支持模型供应商类型：" + normalized);
        }
        return capability;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("模型供应商类型不能为空");
        }
        return value.trim();
    }
}

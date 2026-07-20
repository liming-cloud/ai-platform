package com.aiplatform.infra.springai.model.provider.vertexai;

import com.aiplatform.infra.springai.model.provider.vertexai.support.VertexAiProviderSupport;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import java.io.IOException;
import java.util.Objects;

/**
 * Vertex AI Spring AI client 工厂，集中装配 GCP service account、project 和 location。
 */
final class VertexAiSpringAiClientFactory {
    private VertexAiSpringAiClientFactory() {
    }

    static boolean supports(SpringAiModelRuntimeRequest request) {
        return VertexAiProviderSupport.supports(request);
    }

    static PredictionServiceSettings predictionServiceSettings(SpringAiModelRuntimeRequest request) {
        validateRequest(request);
        String endpoint = VertexAiProviderSupport.apiEndpoint(request)
                .orElseThrow(() -> new IllegalArgumentException("Vertex AI provider endpoint 不能为空"));
        try {
            PredictionServiceSettings.Builder builder = PredictionServiceSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials(request)));
            builder.setEndpoint(endpoint);
            return builder.build();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Vertex AI PredictionServiceSettings 构建失败：" + exception.getMessage());
        }
    }

    private static GoogleCredentials credentials(SpringAiModelRuntimeRequest request) {
        return com.aiplatform.infra.springai.model.provider.vertexai.support.VertexAiSpringAiCredentialFactory
                .create(request.credentialMaterial());
    }

    private static void validateRequest(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        if (!supports(request)) {
            throw new IllegalArgumentException("Vertex AI 模型供应商类型必须是 vertex-ai");
        }
        if (!VertexAiProviderSupport.AUTH_TYPE_GCP_SERVICE_ACCOUNT.equalsIgnoreCase(request.authType())) {
            throw new IllegalArgumentException("Vertex AI 模型供应商仅支持 gcp_service_account 鉴权");
        }
        if (request.credentialMaterial() == null || request.credentialMaterial().isBlank()) {
            throw new IllegalArgumentException("Vertex AI 模型供应商 GCP service account credential material 未配置");
        }
    }
}

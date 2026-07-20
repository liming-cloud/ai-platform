package com.aiplatform.infra.springai.model.provider.vertexai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.springai.model.provider.vertexai.support.VertexAiProviderSupport;
import com.aiplatform.infra.springai.model.provider.vertexai.strategy.VertexAiSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.registry.SpringAiModelOptionsRegistry;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.strategy.StandardSpringAiModelOptionsStrategy;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingModel;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingOptions;

class VertexAiSpringAiModelProbeClientTest {
    private final VertexAiSpringAiModelOptionsStrategy optionsStrategy = new VertexAiSpringAiModelOptionsStrategy();

    @Test
    void registrySelectsVertexEmbeddingOptionsStrategyBeforeStandardStrategy() throws Exception {
        SpringAiModelOptionsRegistry registry = new SpringAiModelOptionsRegistry(List.of(
                new StandardSpringAiModelOptionsStrategy(),
                optionsStrategy
        ));
        SpringAiModelRuntimeRequest request = embeddingRequest(Map.of(
                "location", "us-central1",
                "publisher", "google",
                "dimensions", 768
        ));

        ModelOptions options = registry.select(request).buildOptions(request);

        assertThat(options).isInstanceOf(VertexAiTextEmbeddingOptions.class);
        assertThat(((VertexAiTextEmbeddingOptions) options).getModel()).isEqualTo("text-embedding-004");
    }

    @Test
    void createsEmbeddingModelThroughSpringAiVertexFactory() throws Exception {
        SpringAiModelRuntimeRequest request = embeddingRequest(Map.of(
                "location", "us-central1",
                "publisher", "google",
                "dimensions", 768,
                "taskType", "RETRIEVAL_DOCUMENT",
                "autoTruncate", true
        ));

        Object options = optionsStrategy.buildOptions(request);
        Object model = new VertexAiEmbeddingModelFactory().create(
                request,
                (VertexAiTextEmbeddingOptions) options
        );

        assertThat(options).isInstanceOf(VertexAiTextEmbeddingOptions.class);
        VertexAiTextEmbeddingOptions embeddingOptions = (VertexAiTextEmbeddingOptions) options;
        assertThat(embeddingOptions.getModel()).isEqualTo("text-embedding-004");
        assertThat(embeddingOptions.getDimensions()).isEqualTo(768);
        assertThat(embeddingOptions.getTaskType()).isEqualTo(VertexAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT);
        assertThat(model).isInstanceOf(VertexAiTextEmbeddingModel.class);
    }

    @Test
    void validatesGcpServiceAccountLocationAndTaskType() throws Exception {
        SpringAiModelRuntimeRequest invalidAuth = new SpringAiModelRuntimeRequest(
                "vertex",
                "vertex-ai",
                "https://us-central1-aiplatform.googleapis.com",
                "api_key",
                "gemini-1.5-pro",
                "CHAT",
                100000,
                null,
                Map.of("location", "us-central1"),
                "sk-not-gcp"
        );
        SpringAiModelRuntimeRequest invalidTaskType = embeddingRequest(Map.of(
                "location", "us-central1",
                "dimensions", 768,
                "taskType", "UNKNOWN_TASK"
        ));
        SpringAiModelRuntimeRequest missingLocation = embeddingRequest(Map.of(
                "publisher", "google",
                "dimensions", 768
        ));
        SpringAiModelRuntimeRequest missingPublisher = embeddingRequest(Map.of(
                "location", "us-central1",
                "dimensions", 768
        ));

        assertThat(optionsStrategy.validate(invalidAuth).summary())
                .contains("Vertex AI provider 必须使用 gcp_service_account 鉴权")
                .doesNotContain("credential material");
        assertThat(optionsStrategy.validate(invalidTaskType).summary())
                .contains("Vertex AI taskType 不支持：UNKNOWN_TASK");
        assertThat(optionsStrategy.validate(missingLocation).summary())
                .contains("Vertex AI provider 必须配置 location");
        assertThat(optionsStrategy.validate(missingPublisher).summary())
                .contains("Vertex AI publisher 不能为空");
    }

    @Test
    void validatesStructureWithoutCredentialButBuildOptionsFailsClosed() {
        SpringAiModelRuntimeRequest request = new SpringAiModelRuntimeRequest(
                "vertex",
                "vertex-ai",
                "https://us-central1-aiplatform.googleapis.com",
                "gcp_service_account",
                "text-embedding-004",
                "EMBEDDING",
                8192,
                768,
                Map.of(
                        "projectId", "aip-test-project",
                        "location", "us-central1",
                        "publisher", "google",
                        "dimensions", 768
                ),
                null
        );

        assertThat(optionsStrategy.validate(request).valid()).isTrue();
        assertThatThrownBy(() -> optionsStrategy.buildOptions(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vertex AI provider 必须配置 GCP service account credential material");
    }

    @Test
    void rejectsMissingVertexPublisherInsteadOfDefaultPublisher() throws Exception {
        SpringAiModelRuntimeRequest request = embeddingRequest(Map.of(
                "location", "us-central1",
                "dimensions", 768
        ));

        assertThatThrownBy(() -> VertexAiProviderSupport.publisher(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vertex AI publisher 不能为空");
    }

    private static SpringAiModelRuntimeRequest embeddingRequest(Map<String, Object> options) throws Exception {
        Map<String, Object> completeOptions = new java.util.HashMap<>(options);
        completeOptions.putIfAbsent("projectId", "aip-test-project");
        return new SpringAiModelRuntimeRequest(
                "vertex",
                "google-vertex-ai",
                "https://us-central1-aiplatform.googleapis.com",
                "gcp_service_account",
                "text-embedding-004",
                "EMBEDDING",
                8192,
                768,
                completeOptions,
                serviceAccountJson()
        );
    }

    private static String serviceAccountJson() throws Exception {
        return """
                {
                  "type": "service_account",
                  "project_id": "aip-test-project",
                  "private_key_id": "test-key",
                  "private_key": "%s",
                  "client_email": "aip-test@aip-test-project.iam.gserviceaccount.com",
                  "client_id": "1234567890",
                  "token_uri": "https://oauth2.googleapis.com/token"
                }
                """.formatted(testPrivateKeyPem().replace("\n", "\\n"));
    }

    private static String testPrivateKeyPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
    }
}

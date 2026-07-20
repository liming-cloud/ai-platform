package com.aiplatform.infra.springai.model.provider.vertexai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.aiplatform.infra.springai.model.provider.vertexai.strategy.VertexAiSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.provider.vertexai.support.VertexAiProviderSupport;
import com.aiplatform.infra.springai.model.provider.vertexai.support.VertexAiSpringAiCredentialFactory;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VertexAiOutboundBoundaryTest {
    private static final String OFFICIAL_PROVIDER_ENDPOINT =
            "https://us-central1-aiplatform.googleapis.com";
    private static final String OFFICIAL_TOKEN_URI = "https://oauth2.googleapis.com/token";

    private final VertexAiSpringAiModelOptionsStrategy optionsStrategy =
            new VertexAiSpringAiModelOptionsStrategy();

    @Test
    void rejectsModelApiEndpointOverrideBeforeCredentialCreation() {
        SpringAiModelRuntimeRequest request = embeddingRequest(
                OFFICIAL_PROVIDER_ENDPOINT,
                Map.of("apiEndpoint", "https://127.0.0.1:8443"),
                "not-a-service-account"
        );

        assertThat(optionsStrategy.validate(request).valid()).isFalse();
        assertThat(optionsStrategy.validate(request).summary())
                .contains("Vertex AI 不支持的模型 options 字段 apiEndpoint");

        Throwable optionsFailure = catchThrowable(() -> optionsStrategy.buildOptions(request));
        Throwable failure = catchThrowable(() -> VertexAiSpringAiClientFactory.predictionServiceSettings(request));

        assertThat(optionsFailure)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiEndpoint");
        assertThat(optionsFailure.getMessage()).doesNotContain("credential material");
        assertThat(failure)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vertex AI 模型 options 不得覆盖 provider endpoint");
        assertThat(failure.getMessage()).doesNotContain("credential material");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://us-central1-aiplatform.googleapis.com",
            "https://127.0.0.1",
            "https://metadata.google.internal",
            "https://us-central1-aiplatform.googleapis.com/v1",
            "https://us-central1-aiplatform.googleapis.com?target=internal",
            "https://user@us-central1-aiplatform.googleapis.com",
            "https://us-central1-aiplatform.googleapis.com:8443",
            "https://us-central1-aiplatform.googleapis.com.evil.test",
            "https://europe-west4-aiplatform.googleapis.com"
    })
    void rejectsUnsafeProviderEndpointBeforeCredentialCreation(String endpoint) {
        SpringAiModelRuntimeRequest request = embeddingRequest(
                endpoint,
                Map.of(),
                "not-a-service-account"
        );

        Throwable optionsFailure = catchThrowable(() -> optionsStrategy.buildOptions(request));
        Throwable failure = catchThrowable(() -> VertexAiSpringAiClientFactory.predictionServiceSettings(request));

        assertThat(optionsStrategy.validate(request).valid()).isFalse();
        assertThat(optionsStrategy.validate(request).summary()).contains("Vertex AI provider endpoint");
        assertThat(optionsFailure)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vertex AI provider endpoint");
        assertThat(optionsFailure.getMessage()).doesNotContain("credential material");
        assertThat(failure)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vertex AI provider endpoint");
        assertThat(failure.getMessage()).doesNotContain("credential material");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "localhost",
            "127.0.0.1",
            "metadata.google.internal",
            "us-central1/../../metadata",
            "us-central1?target=internal"
    })
    void rejectsUnsafeLocationBeforeCredentialCreation(String location) {
        SpringAiModelRuntimeRequest request = embeddingRequest(
                OFFICIAL_PROVIDER_ENDPOINT,
                Map.of("location", location),
                "not-a-service-account"
        );

        Throwable optionsFailure = catchThrowable(() -> optionsStrategy.buildOptions(request));
        Throwable failure = catchThrowable(() -> VertexAiSpringAiClientFactory.predictionServiceSettings(request));

        assertThat(optionsStrategy.validate(request).valid()).isFalse();
        assertThat(optionsStrategy.validate(request).summary()).contains("Vertex AI location");
        assertThat(optionsFailure)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vertex AI location");
        assertThat(optionsFailure.getMessage()).doesNotContain("credential material");
        assertThat(failure)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vertex AI location");
        assertThat(failure.getMessage()).doesNotContain("credential material");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            OFFICIAL_PROVIDER_ENDPOINT,
            OFFICIAL_PROVIDER_ENDPOINT + ":443"
    })
    void normalizesOfficialRegionalProviderEndpointToTlsPort(String endpoint) {
        SpringAiModelRuntimeRequest request = embeddingRequest(
                endpoint,
                Map.of(),
                "not-a-service-account"
        );

        assertThat(VertexAiProviderSupport.apiEndpoint(request))
                .contains("us-central1-aiplatform.googleapis.com:443");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            OFFICIAL_TOKEN_URI,
            "https://oauth2.googleapis.com:443/token"
    })
    void acceptsOfficialHttpsGoogleOAuthTokenEndpoint(String tokenUri) throws Exception {
        assertThat(VertexAiSpringAiCredentialFactory.create(serviceAccountJson(tokenUri, testPrivateKeyPem())))
                .isNotNull();
    }

    @Test
    void rejectsNonOfficialTokenUriFromOtherwiseValidServiceAccount() throws Exception {
        String credentialMaterial = serviceAccountJson(
                "https://metadata.google.internal/token",
                testPrivateKeyPem()
        );

        assertThatThrownBy(() -> VertexAiSpringAiCredentialFactory.create(credentialMaterial))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token_uri 必须是官方 HTTPS Google OAuth token endpoint");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://oauth2.googleapis.com/token",
            "https://127.0.0.1/token",
            "https://metadata.google.internal/token",
            "https://oauth2.googleapis.com/token/extra",
            "https://oauth2.googleapis.com/token?target=internal",
            "https://user@oauth2.googleapis.com/token",
            "https://oauth2.googleapis.com:8443/token",
            "https://oauth2.googleapis.com.evil.test/token"
    })
    void rejectsUnsafeTokenUriBeforeGoogleCredentialCreation(String tokenUri) {
        String credentialMaterial = serviceAccountJson(tokenUri, "not-a-private-key");

        assertThatThrownBy(() -> VertexAiSpringAiCredentialFactory.create(credentialMaterial))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token_uri 必须是官方 HTTPS Google OAuth token endpoint")
                .hasMessageNotContaining("credential material 必须是合法 service_account JSON");
    }

    private static SpringAiModelRuntimeRequest embeddingRequest(
            String endpoint,
            Map<String, Object> additionalOptions,
            String credentialMaterial
    ) {
        Map<String, Object> options = new HashMap<>(Map.of(
                "projectId", "aip-test-project",
                "location", "us-central1",
                "publisher", "google",
                "dimensions", 768
        ));
        options.putAll(additionalOptions);
        return new SpringAiModelRuntimeRequest(
                "vertex",
                "vertex-ai",
                endpoint,
                "gcp_service_account",
                "text-embedding-004",
                "EMBEDDING",
                8192,
                768,
                options,
                credentialMaterial
        );
    }

    private static String serviceAccountJson(String tokenUri, String privateKey) {
        return """
                {
                  "type": "service_account",
                  "project_id": "aip-test-project",
                  "private_key_id": "test-key",
                  "private_key": "%s",
                  "client_email": "aip-test@aip-test-project.iam.gserviceaccount.com",
                  "client_id": "1234567890",
                  "token_uri": "%s"
                }
                """.formatted(privateKey.replace("\n", "\\n"), tokenUri);
    }

    private static String testPrivateKeyPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
    }
}

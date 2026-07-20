package com.aiplatform.infra.springai.model.provider.azureopenai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.springai.http.SecureModelHttpTransport;
import com.aiplatform.infra.springai.model.provider.azureopenai.strategy.AzureOpenAiSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.registry.SpringAiModelOptionsRegistry;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelConnectivityResult;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelRuntimeProbe;
import com.aiplatform.infra.springai.model.strategy.StandardSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.test.TestModelHttpTransport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

class AzureOpenAiSpringAiModelProbeClientTest {
    private final SecureModelHttpTransport transport = TestModelHttpTransport.permissiveLocalTransport();
    private final AzureOpenAiSpringAiModelOptionsStrategy optionsStrategy =
            new AzureOpenAiSpringAiModelOptionsStrategy();
    private final AzureOpenAiSpringAiModelProbeClient probeClient = new AzureOpenAiSpringAiModelProbeClient(
            new AzureOpenAiChatModelFactory(transport),
            new AzureOpenAiEmbeddingModelFactory(transport)
    );

    @AfterEach
    void closeTransport() {
        transport.close();
    }

    @Test
    void registrySelectsAzureOptionsStrategyBeforeStandardStrategy() {
        SpringAiModelOptionsRegistry registry = new SpringAiModelOptionsRegistry(List.of(
                new StandardSpringAiModelOptionsStrategy(),
                optionsStrategy
        ));
        SpringAiModelRuntimeRequest request = chatRequest(
                "https://aip-test.openai.azure.com",
                Map.of("deploymentName", "gpt4o-prod", "apiVersion", "2024-06-01", "temperature", 0.2)
        );

        ModelOptions options = registry.select(request).buildOptions(request);

        assertThat(options).isInstanceOf(OpenAiChatOptions.class);
        OpenAiChatOptions chatOptions = (OpenAiChatOptions) options;
        assertThat(chatOptions.getBaseUrl()).isEqualTo("https://aip-test.openai.azure.com");
        assertThat(chatOptions.getDeploymentName()).isEqualTo("gpt4o-prod");
        assertThat(chatOptions.getMicrosoftFoundryServiceVersion().value()).isEqualTo("2024-06-01");
        assertThat(chatOptions.isMicrosoftFoundry()).isTrue();
    }

    @Test
    void createsChatModelThroughSpringAiAzureOpenAiFactory() {
        SpringAiModelRuntimeRequest request = chatRequest(
                "https://aip-test.openai.azure.com",
                Map.of("deploymentName", "gpt4o-prod", "apiVersion", "2024-06-01", "maxTokens", 1)
        );

        Object model = new AzureOpenAiChatModelFactory(transport).create(
                request,
                (OpenAiChatOptions) optionsStrategy.buildOptions(request)
        );

        assertThat(model).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void createsEmbeddingModelThroughSpringAiAzureOpenAiFactory() {
        SpringAiModelRuntimeRequest request = embeddingRequest("https://aip-test.openai.azure.com");

        Object model = new AzureOpenAiEmbeddingModelFactory(transport).create(
                request,
                (OpenAiEmbeddingOptions) optionsStrategy.buildOptions(request)
        );

        assertThat(model).isInstanceOf(OpenAiEmbeddingModel.class);
    }

    @Test
    void validatesAzureAuthAndApiVersion() {
        SpringAiModelRuntimeRequest invalidAuth = new SpringAiModelRuntimeRequest(
                "azure-openai",
                "azure_openai",
                "https://aip-test.openai.azure.com",
                "no_auth",
                "gpt4o-prod",
                "CHAT",
                128000,
                null,
                Map.of("deploymentName", "gpt4o-prod", "apiVersion", "2024-06-01"),
                null
        );
        SpringAiModelRuntimeRequest futureVersion = chatRequest(
                "https://aip-test.openai.azure.com",
                Map.of("deploymentName", "gpt4o-prod", "apiVersion", "2099-01-01")
        );

        assertThat(optionsStrategy.validate(invalidAuth).summary())
                .contains("Azure OpenAI provider 必须使用 api_key 鉴权")
                .doesNotContain("Azure OpenAI provider 必须配置 API Key");
        assertThat(optionsStrategy.validate(futureVersion).valid()).isTrue();
        assertThat(((OpenAiChatOptions) optionsStrategy.buildOptions(futureVersion))
                .getMicrosoftFoundryServiceVersion().value()).isEqualTo("2099-01-01");
    }

    @Test
    void validatesStructureWithoutCredentialButBuildOptionsFailsClosed() {
        SpringAiModelRuntimeRequest request = new SpringAiModelRuntimeRequest(
                "azure-openai",
                "azure-openai",
                "https://aip-test.openai.azure.com",
                "api_key",
                "gpt4o-prod",
                "CHAT",
                128000,
                null,
                Map.of("deploymentName", "gpt4o-prod", "apiVersion", "2024-06-01"),
                null
        );

        assertThat(optionsStrategy.validate(request).valid()).isTrue();
        assertThatThrownBy(() -> optionsStrategy.buildOptions(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Azure OpenAI provider 必须配置 API Key");
    }

    @Test
    void rejectsMissingAzureDeploymentNameInsteadOfModelCodeFallback() {
        SpringAiModelRuntimeRequest request = chatRequest(
                "https://aip-test.openai.azure.com",
                Map.of("apiVersion", "2024-06-01")
        );

        assertThat(optionsStrategy.validate(request).summary())
                .contains("Azure OpenAI deploymentName 不能为空");
        assertThatThrownBy(() -> optionsStrategy.buildOptions(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Azure OpenAI deploymentName 不能为空");
    }

    @Test
    void rejectsMissingAzureApiVersionInsteadOfDefaultFallback() {
        SpringAiModelRuntimeRequest request = chatRequest(
                "https://aip-test.openai.azure.com",
                Map.of("deploymentName", "gpt4o-prod")
        );

        assertThat(optionsStrategy.validate(request).summary())
                .contains("Azure OpenAI api-version 不能为空");
        assertThatThrownBy(() -> optionsStrategy.buildOptions(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Azure OpenAI api-version 不能为空");
    }

    @Test
    void buildsAzureEmbeddingOptionsWithDeploymentDimensionAndUser() {
        SpringAiModelRuntimeRequest request = embeddingRequest("https://aip-test.openai.azure.com");

        ModelOptions options = optionsStrategy.buildOptions(request);

        assertThat(options).isInstanceOf(OpenAiEmbeddingOptions.class);
        OpenAiEmbeddingOptions embeddingOptions = (OpenAiEmbeddingOptions) options;
        assertThat(embeddingOptions.getDeploymentName()).isEqualTo("embed-prod");
        assertThat(embeddingOptions.getMicrosoftFoundryServiceVersion().value()).isEqualTo("2024-06-01");
        assertThat(embeddingOptions.isMicrosoftFoundry()).isTrue();
        assertThat(embeddingOptions.getDimensions()).isEqualTo(1536);
        assertThat(embeddingOptions.getUser()).isEqualTo("tenant-2001");
    }

    @Test
    void redactsApiKeyWhenNetworkFails() {
        SpringAiModelRuntimeRequest request = chatRequest(
                "https://127.0.0.1:9",
                Map.of("deploymentName", "gpt4o-prod", "apiVersion", "2024-06-01", "maxTokens", 1)
        );

        SpringAiModelConnectivityResult result = probeClient.probe(new SpringAiModelRuntimeProbe(
                request,
                optionsStrategy.buildOptions(request)
        ));

        assertThat(result.connected()).isFalse();
        assertThat(result.message()).contains("Azure OpenAI 网络连接失败");
        assertThat(result.message()).doesNotContain("sk-azure-test");
    }

    @Test
    void rejectsPlainHttpEndpointBeforeAzureSdkCanLeakKey() {
        SpringAiModelRuntimeRequest request = chatRequest(
                "http://127.0.0.1:8080",
                Map.of("deploymentName", "gpt4o-prod", "apiVersion", "2024-06-01", "maxTokens", 1)
        );

        assertThat(optionsStrategy.validate(request).summary())
                .contains("Azure OpenAI endpoint 必须使用 HTTPS")
                .doesNotContain("sk-azure-test");
    }

    private static SpringAiModelRuntimeRequest chatRequest(String endpoint, Map<String, Object> options) {
        return new SpringAiModelRuntimeRequest(
                "azure-openai",
                "azure_openai",
                endpoint,
                "api_key",
                "gpt4o-prod",
                "CHAT",
                128000,
                null,
                options,
                "sk-azure-test"
        );
    }

    private static SpringAiModelRuntimeRequest embeddingRequest(String endpoint) {
        return new SpringAiModelRuntimeRequest(
                "azure-openai",
                "azure-openai",
                endpoint,
                "api_key",
                "embed-prod",
                "EMBEDDING",
                8192,
                1536,
                Map.of(
                        "deploymentName", "embed-prod",
                        "apiVersion", "2024-06-01",
                        "dimensions", 1536,
                        "user", "tenant-2001"
                ),
                "sk-azure-test"
        );
    }

}

package com.aiplatform.infra.springai.model.provider.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.springai.model.provider.bedrock.support.BedrockProviderSupport;
import com.aiplatform.infra.springai.model.provider.bedrock.strategy.BedrockSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.registry.SpringAiModelOptionsRegistry;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.strategy.StandardSpringAiModelOptionsStrategy;
import com.aiplatform.shared.kernel.ModelApprovalRuntimeOptions;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.bedrock.cohere.BedrockCohereEmbeddingModel;
import org.springframework.ai.bedrock.cohere.BedrockCohereEmbeddingOptions;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingModel;
import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingOptions;
import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

class BedrockSpringAiModelProbeClientTest {
    private final BedrockSpringAiModelOptionsStrategy optionsStrategy = new BedrockSpringAiModelOptionsStrategy();

    @Test
    void registrySelectsBedrockOptionsStrategyBeforeStandardStrategy() {
        SpringAiModelOptionsRegistry registry = new SpringAiModelOptionsRegistry(List.of(
                new StandardSpringAiModelOptionsStrategy(),
                optionsStrategy
        ));
        SpringAiModelRuntimeRequest request = chatRequest(Map.of(
                "region", "us-east-1",
                "timeoutSeconds", 15,
                "maxTokens", 1,
                "temperature", 0.2
        ));

        ModelOptions options = registry.select(request).buildOptions(request);

        assertThat(options).isInstanceOf(ToolCallingChatOptions.class);
        ToolCallingChatOptions chatOptions = (ToolCallingChatOptions) options;
        assertThat(chatOptions.getModel()).isEqualTo("anthropic.claude-3-5-sonnet-20240620-v1:0");
        assertThat(chatOptions.getTemperature()).isEqualTo(0.2);
    }

    @Test
    void createsChatModelThroughSpringAiBedrockFactory() {
        SpringAiModelRuntimeRequest request = chatRequest(Map.of(
                "region", "us-east-1",
                "timeoutSeconds", 15,
                "maxTokens", 1
        ));

        Object model = new BedrockChatModelFactory().create(
                request,
                (ToolCallingChatOptions) optionsStrategy.buildOptions(request)
        );

        assertThat(model).isInstanceOf(BedrockProxyChatModel.class);
    }

    @Test
    void bindsSyncAndAsyncChatClientsToTheValidatedProviderEndpoint() {
        SpringAiModelRuntimeRequest request = chatRequest(Map.of(
                "region", "us-east-1",
                "timeoutSeconds", 15,
                "maxTokens", 1
        ));
        URI endpoint = URI.create("https://bedrock-runtime.us-east-1.amazonaws.com");

        try (BedrockRuntimeClient syncClient = BedrockSpringAiClientFactory.bedrockRuntimeClient(request);
             BedrockRuntimeAsyncClient asyncClient = BedrockSpringAiClientFactory.bedrockRuntimeAsyncClient(request)) {
            assertThat(syncClient.serviceClientConfiguration().endpointOverride()).contains(endpoint);
            assertThat(asyncClient.serviceClientConfiguration().endpointOverride()).contains(endpoint);
        }
    }

    @Test
    void createsTitanEmbeddingModelThroughSpringAiFactory() {
        SpringAiModelRuntimeRequest request = embeddingRequest(
                "amazon.titan-embed-text-v2:0",
                Map.of("region", "us-east-1", "timeoutSeconds", 15, "embeddingProvider", "titan", "inputType", "TEXT")
        );

        ModelOptions options = optionsStrategy.buildOptions(request);
        Object model = new BedrockEmbeddingModelFactory().create(
                request,
                (BedrockTitanEmbeddingOptions) options
        );

        assertThat(options).isInstanceOf(BedrockTitanEmbeddingOptions.class);
        assertThat(((BedrockTitanEmbeddingOptions) options).getInputType())
                .isEqualTo(BedrockTitanEmbeddingModel.InputType.TEXT);
        assertThat(model).isInstanceOf(BedrockTitanEmbeddingModel.class);
    }

    @Test
    void createsCohereEmbeddingModelThroughSpringAiFactory() {
        SpringAiModelRuntimeRequest request = embeddingRequest(
                "cohere.embed-multilingual-v3",
                ModelApprovalRuntimeOptions.validateAndNormalize(Map.of(
                        "providerKind", "bedrock",
                        "region", " us-east-1 ",
                        "timeoutSeconds", 15,
                        "embeddingProvider", " cohere ",
                        "inputType", " SEARCH_DOCUMENT ",
                        "truncate", " END "
                )).entrySet().stream()
                        .filter(entry -> !"providerKind".equals(entry.getKey()))
                        .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue))
        );

        ModelOptions options = optionsStrategy.buildOptions(request);
        Object model = new BedrockEmbeddingModelFactory().create(
                request,
                (BedrockCohereEmbeddingOptions) options
        );

        assertThat(options).isInstanceOf(BedrockCohereEmbeddingOptions.class);
        assertThat(((BedrockCohereEmbeddingOptions) options).getTruncate())
                .isEqualTo(org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest.Truncate.END);
        assertThat(model).isInstanceOf(BedrockCohereEmbeddingModel.class);
    }

    @Test
    void validatesAwsCredentialsAuthRegionAndPrivateOptions() {
        SpringAiModelRuntimeRequest invalidAuth = new SpringAiModelRuntimeRequest(
                "bedrock",
                "bedrock",
                "https://bedrock-runtime.us-east-1.amazonaws.com",
                "api_key",
                "anthropic.claude-3-5-sonnet-20240620-v1:0",
                "CHAT",
                200000,
                null,
                Map.of("region", "us-east-1", "timeoutSeconds", 15),
                "sk-not-aws"
        );
        SpringAiModelRuntimeRequest invalidOption = chatRequest(Map.of(
                "region", "us-east-1",
                "timeoutSeconds", 15,
                "vendorMagic", true
        ));
        SpringAiModelRuntimeRequest missingRegion = new SpringAiModelRuntimeRequest(
                "bedrock",
                "bedrock",
                "https://bedrock-runtime.us-east-1.amazonaws.com",
                "aws_credentials",
                "anthropic.claude-3-5-sonnet-20240620-v1:0",
                "CHAT",
                200000,
                null,
                Map.of("maxTokens", 1, "timeoutSeconds", 15),
                awsCredentialMaterial()
        );
        SpringAiModelRuntimeRequest missingTimeout = chatRequest(Map.of("region", "us-east-1", "maxTokens", 1));

        assertThat(optionsStrategy.validate(invalidAuth).summary())
                .contains("Amazon Bedrock provider 必须使用 aws_credentials 鉴权")
                .doesNotContain("credential material");
        assertThat(optionsStrategy.validate(invalidOption).summary())
                .contains("Amazon Bedrock 不支持的模型 options 字段 vendorMagic");
        assertThat(optionsStrategy.validate(missingRegion).summary())
                .contains("Amazon Bedrock provider 必须配置 region");
        assertThat(optionsStrategy.validate(missingTimeout).summary())
                .contains("Amazon Bedrock timeoutSeconds 不能为空");
    }

    @Test
    void validatesStructureWithoutCredentialButBuildOptionsFailsClosed() {
        SpringAiModelRuntimeRequest request = new SpringAiModelRuntimeRequest(
                "bedrock",
                "bedrock",
                "https://bedrock-runtime.us-east-1.amazonaws.com",
                "aws_credentials",
                "anthropic.claude-3-5-sonnet-20240620-v1:0",
                "CHAT",
                200000,
                null,
                Map.of("region", "us-east-1", "timeoutSeconds", 15),
                null
        );

        assertThat(optionsStrategy.validate(request).valid()).isTrue();
        assertThatThrownBy(() -> optionsStrategy.buildOptions(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amazon Bedrock provider 必须配置 AWS credential material");
    }

    @Test
    void rejectsMissingBedrockTimeoutInsteadOfDefaultTimeout() {
        SpringAiModelRuntimeRequest request = chatRequest(Map.of("region", "us-east-1", "maxTokens", 1));
        SpringAiModelRuntimeRequest explicitTimeout = chatRequest(Map.of(
                "region", "us-east-1",
                "timeoutSeconds", 15,
                "maxTokens", 1
        ));

        assertThatThrownBy(() -> BedrockSpringAiClientFactory.timeout(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amazon Bedrock timeoutSeconds 不能为空");
        assertThat(BedrockSpringAiClientFactory.timeout(explicitTimeout)).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void rejectsMissingBedrockEmbeddingProviderInsteadOfInferringFromModelCode() {
        SpringAiModelRuntimeRequest request = embeddingRequest(
                "cohere.embed-multilingual-v3",
                Map.of("region", "us-east-1", "timeoutSeconds", 15)
        );

        assertThat(optionsStrategy.validate(request).summary())
                .contains("Amazon Bedrock embeddingProvider 不能为空");
        assertThatThrownBy(() -> BedrockProviderSupport.embeddingProvider(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amazon Bedrock embeddingProvider 不能为空");
    }

    private static SpringAiModelRuntimeRequest chatRequest(Map<String, Object> options) {
        return new SpringAiModelRuntimeRequest(
                "bedrock",
                "amazon-bedrock",
                "https://bedrock-runtime.us-east-1.amazonaws.com",
                "aws_credentials",
                "anthropic.claude-3-5-sonnet-20240620-v1:0",
                "CHAT",
                200000,
                null,
                options,
                awsCredentialMaterial()
        );
    }

    private static SpringAiModelRuntimeRequest embeddingRequest(String modelCode, Map<String, Object> options) {
        return new SpringAiModelRuntimeRequest(
                "bedrock",
                "aws_bedrock",
                "https://bedrock-runtime.us-east-1.amazonaws.com",
                "aws_credentials",
                modelCode,
                "EMBEDDING",
                8192,
                1024,
                options,
                awsCredentialMaterial()
        );
    }

    private static String awsCredentialMaterial() {
        return """
                {
                  "accessKeyId": "AKIATEST1234567890",
                  "secretAccessKey": "bedrock-secret",
                  "sessionToken": "bedrock-session-token",
                  "region": "us-east-1"
                }
                """;
    }

}

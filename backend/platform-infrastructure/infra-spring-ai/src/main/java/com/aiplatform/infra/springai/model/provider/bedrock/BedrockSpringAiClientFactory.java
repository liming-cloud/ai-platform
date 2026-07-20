package com.aiplatform.infra.springai.model.provider.bedrock;

import com.aiplatform.infra.springai.model.provider.bedrock.support.BedrockProviderSupport;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi;
import org.springframework.ai.util.JacksonUtils;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Amazon Bedrock Spring AI client 工厂，集中装配 AWS SDK 凭证和区域。
 */
final class BedrockSpringAiClientFactory {
    private BedrockSpringAiClientFactory() {
    }

    static boolean supports(SpringAiModelRuntimeRequest request) {
        return BedrockProviderSupport.supports(request);
    }

    static AwsCredentialsProvider credentialsProvider(SpringAiModelRuntimeRequest request) {
        validateRequest(request);
        return com.aiplatform.infra.springai.model.provider.bedrock.support.BedrockSpringAiCredentialsFactory
                .create(request.credentialMaterial());
    }

    static Region region(SpringAiModelRuntimeRequest request) {
        return BedrockProviderSupport.region(request);
    }

    static Duration timeout(SpringAiModelRuntimeRequest request) {
        return com.aiplatform.infra.springai.model.strategy.support.ModelOptionValues
                .integerValue(request.options(), BedrockProviderSupport.TIMEOUT_SECONDS_OPTION)
                .map(Duration::ofSeconds)
                .orElseThrow(() -> new IllegalArgumentException("Amazon Bedrock timeoutSeconds 不能为空"));
    }

    static BedrockRuntimeClient bedrockRuntimeClient(SpringAiModelRuntimeRequest request) {
        validateRequest(request);
        return BedrockRuntimeClient.builder()
                .credentialsProvider(credentialsProvider(request))
                .region(region(request))
                .endpointOverride(URI.create(request.endpoint()))
                .overrideConfiguration(clientOverrideConfiguration(request))
                .build();
    }

    static BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient(SpringAiModelRuntimeRequest request) {
        validateRequest(request);
        return BedrockRuntimeAsyncClient.builder()
                .credentialsProvider(credentialsProvider(request))
                .region(region(request))
                .endpointOverride(URI.create(request.endpoint()))
                .overrideConfiguration(clientOverrideConfiguration(request))
                .build();
    }

    static TitanEmbeddingBedrockApi titanEmbeddingApi(SpringAiModelRuntimeRequest request) {
        validateRequest(request);
        return new TitanEmbeddingBedrockApi(
                request.modelCode(),
                credentialsProvider(request),
                region(request),
                JacksonUtils.getDefaultJsonMapper(),
                timeout(request)
        );
    }

    static CohereEmbeddingBedrockApi cohereEmbeddingApi(SpringAiModelRuntimeRequest request) {
        validateRequest(request);
        return new CohereEmbeddingBedrockApi(
                request.modelCode(),
                credentialsProvider(request),
                region(request),
                JacksonUtils.getDefaultJsonMapper(),
                timeout(request)
        );
    }

    private static void validateRequest(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        if (!supports(request)) {
            throw new IllegalArgumentException("Amazon Bedrock 模型供应商类型必须是 bedrock");
        }
        if (!BedrockProviderSupport.AUTH_TYPE_AWS_CREDENTIALS.equalsIgnoreCase(request.authType())) {
            throw new IllegalArgumentException("Amazon Bedrock 模型供应商仅支持 aws_credentials 鉴权");
        }
        BedrockProviderSupport.requireCanonicalEndpointForRegion(request);
        if (request.credentialMaterial() == null || request.credentialMaterial().isBlank()) {
            throw new IllegalArgumentException("Amazon Bedrock 模型供应商 AWS credential material 未配置");
        }
    }

    private static ClientOverrideConfiguration clientOverrideConfiguration(SpringAiModelRuntimeRequest request) {
        return ClientOverrideConfiguration.builder()
                .apiCallAttemptTimeout(timeout(request))
                .apiCallTimeout(timeout(request))
                .build();
    }
}

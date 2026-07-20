package com.aiplatform.infra.springai.model.provider.bedrock.support;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.strategy.support.ModelOptionValues;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import software.amazon.awssdk.regions.Region;

/**
 * Amazon Bedrock provider 共享规则，集中约束 provider kind、鉴权方式和区域来源。
 */
public final class BedrockProviderSupport {
    public static final String AUTH_TYPE_AWS_CREDENTIALS = "aws_credentials";
    public static final String REGION_OPTION = "region";
    public static final String TIMEOUT_SECONDS_OPTION = "timeoutSeconds";
    public static final String EMBEDDING_PROVIDER_OPTION = "embeddingProvider";
    public static final String INPUT_TYPE_OPTION = "inputType";
    public static final String TRUNCATE_OPTION = "truncate";
    public static final String EMBEDDING_PROVIDER_TITAN = "titan";
    public static final String EMBEDDING_PROVIDER_COHERE = "cohere";

    private static final Set<String> SUPPORTED_PROVIDER_KINDS = Set.of(
            "bedrock",
            "amazon-bedrock",
            "amazon_bedrock",
            "aws-bedrock",
            "aws_bedrock"
    );

    private BedrockProviderSupport() {
    }

    public static boolean supports(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        return SUPPORTED_PROVIDER_KINDS.contains(normalize(request.providerKind()));
    }

    public static Region region(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        String region = regionText(request)
                .orElseThrow(() -> new IllegalArgumentException("Amazon Bedrock provider 必须配置 region"));
        return Region.of(region);
    }

    public static Optional<String> regionText(SpringAiModelRuntimeRequest request) {
        return ModelOptionValues.stringValue(request.options(), REGION_OPTION);
    }

    public static void requireCanonicalEndpointForRegion(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        requireCanonicalEndpointForRegion(request.providerKind(), request.endpoint(), request.options());
    }

    public static void requireCanonicalEndpointForRegion(
            String providerKind,
            String endpoint,
            Map<String, Object> effectiveOptions
    ) {
        if (!SUPPORTED_PROVIDER_KINDS.contains(normalize(providerKind))) {
            return;
        }
        Objects.requireNonNull(effectiveOptions, "Amazon Bedrock effective options 不能为空");
        String region = ModelOptionValues.stringValue(effectiveOptions, REGION_OPTION)
                .map(BedrockProviderSupport::normalize)
                .orElseThrow(() -> new IllegalArgumentException("Amazon Bedrock provider 必须配置 region"));
        URI uri;
        try {
            uri = URI.create(Objects.requireNonNull(endpoint, "Amazon Bedrock provider endpoint 不能为空"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Amazon Bedrock provider endpoint 格式非法");
        }
        String dnsSuffix = region.startsWith("cn-") ? "amazonaws.com.cn" : "amazonaws.com";
        String expectedHost = "bedrock-runtime." + region + "." + dnsSuffix;
        String path = uri.getRawPath();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getRawUserInfo() != null
                || uri.getHost() == null
                || !expectedHost.equalsIgnoreCase(uri.getHost())
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || (path != null && !path.isEmpty() && !"/".equals(path))
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Amazon Bedrock provider endpoint 与 region 不一致");
        }
    }

    public static String embeddingProvider(SpringAiModelRuntimeRequest request) {
        return embeddingProviderText(request)
                .orElseThrow(() -> new IllegalArgumentException("Amazon Bedrock embeddingProvider 不能为空"));
    }

    public static Optional<String> embeddingProviderText(SpringAiModelRuntimeRequest request) {
        return ModelOptionValues.stringValue(request.options(), EMBEDDING_PROVIDER_OPTION)
                .map(BedrockProviderSupport::normalize);
    }

    public static boolean isTitanEmbedding(SpringAiModelRuntimeRequest request) {
        return EMBEDDING_PROVIDER_TITAN.equals(embeddingProvider(request));
    }

    public static boolean isCohereEmbedding(SpringAiModelRuntimeRequest request) {
        return EMBEDDING_PROVIDER_COHERE.equals(embeddingProvider(request));
    }

    public static Map<String, Object> credentialMap(String credentialMaterial) {
        return BedrockSpringAiCredentialsFactory.credentialMap(credentialMaterial);
    }

    public static String normalize(String value) {
        return Objects.requireNonNull(value, "运行时字段不能为空").trim().toLowerCase(Locale.ROOT);
    }
}

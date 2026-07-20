package com.aiplatform.infra.springai.model.provider.vertexai.support;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.strategy.support.ModelOptionValues;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Vertex AI provider 共享规则，集中约束 provider kind、鉴权方式、项目和区域参数。
 */
public final class VertexAiProviderSupport {
    public static final String AUTH_TYPE_GCP_SERVICE_ACCOUNT = "gcp_service_account";
    public static final String PROJECT_ID_OPTION = "projectId";
    public static final String LOCATION_OPTION = "location";
    public static final String API_ENDPOINT_OPTION = "apiEndpoint";
    public static final String PUBLISHER_OPTION = "publisher";
    public static final String TASK_TYPE_OPTION = "taskType";
    public static final String TITLE_OPTION = "title";
    public static final String AUTO_TRUNCATE_OPTION = "autoTruncate";
    public static final String RESPONSE_MIME_TYPE_OPTION = "responseMimeType";
    public static final String GOOGLE_SEARCH_RETRIEVAL_OPTION = "googleSearchRetrieval";
    public static final String CANDIDATE_COUNT_OPTION = "candidateCount";
    public static final String MAX_OUTPUT_TOKENS_OPTION = "maxOutputTokens";

    private static final Set<String> SUPPORTED_PROVIDER_KINDS = Set.of(
            "vertex-ai",
            "vertex_ai",
            "vertexai",
            "google-vertex-ai",
            "google_vertex_ai",
            "gcp-vertex-ai",
            "gcp_vertex_ai"
    );
    private static final Pattern REGIONAL_LOCATION = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)+");

    private VertexAiProviderSupport() {
    }

    public static boolean supports(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        return SUPPORTED_PROVIDER_KINDS.contains(normalize(request.providerKind()));
    }

    public static String projectId(SpringAiModelRuntimeRequest request) {
        return projectIdText(request)
                .orElseThrow(() -> new IllegalArgumentException("Vertex AI provider 必须配置 projectId"));
    }

    public static Optional<String> projectIdText(SpringAiModelRuntimeRequest request) {
        return ModelOptionValues.stringValue(request.options(), PROJECT_ID_OPTION)
                .or(() -> VertexAiSpringAiCredentialFactory.projectId(request.credentialMaterial()));
    }

    public static String location(SpringAiModelRuntimeRequest request) {
        String location = ModelOptionValues.stringValue(request.options(), LOCATION_OPTION)
                .orElseThrow(() -> new IllegalArgumentException("Vertex AI provider 必须配置 location"));
        String normalized = normalize(location);
        if (!REGIONAL_LOCATION.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Vertex AI location 必须是安全的 regional location");
        }
        return normalized;
    }

    public static String publisher(SpringAiModelRuntimeRequest request) {
        return ModelOptionValues.stringValue(request.options(), PUBLISHER_OPTION)
                .orElseThrow(() -> new IllegalArgumentException("Vertex AI publisher 不能为空"));
    }

    public static Optional<String> apiEndpoint(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        if (request.options().containsKey(API_ENDPOINT_OPTION)) {
            throw new IllegalArgumentException("Vertex AI 模型 options 不得覆盖 provider endpoint");
        }
        return Optional.of(normalizeEndpoint(request.endpoint(), location(request)));
    }

    private static String normalizeEndpoint(String endpoint, String location) {
        URI uri;
        try {
            uri = new URI(Objects.requireNonNull(endpoint, "Vertex AI provider endpoint 不能为空").trim());
        } catch (URISyntaxException exception) {
            throw invalidEndpoint();
        }
        String expectedHost = location + "-aiplatform.googleapis.com";
        int port = uri.getPort();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getRawUserInfo() != null
                || uri.getHost() == null
                || !expectedHost.equalsIgnoreCase(uri.getHost())
                || (port != -1 && port != 443)
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw invalidEndpoint();
        }
        return expectedHost + ":443";
    }

    private static IllegalArgumentException invalidEndpoint() {
        return new IllegalArgumentException(
                "Vertex AI provider endpoint 必须是与 location 匹配的官方 HTTPS "
                        + "regional googleapis.com:443 地址"
        );
    }

    public static String normalize(String value) {
        return Objects.requireNonNull(value, "运行时字段不能为空").trim().toLowerCase(Locale.ROOT);
    }
}

package com.aiplatform.infra.springai.model.provider.vertexai.support;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 将受控 credential material 转为 Google service account 凭证对象。
 */
public final class VertexAiSpringAiCredentialFactory {
    private static final List<String> CLOUD_PLATFORM_SCOPE = List.of("https://www.googleapis.com/auth/cloud-platform");
    private static final JsonMapper OBJECT_MAPPER = new JsonMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String OFFICIAL_TOKEN_HOST = "oauth2.googleapis.com";
    private static final String OFFICIAL_TOKEN_PATH = "/token";

    private VertexAiSpringAiCredentialFactory() {
    }

    public static GoogleCredentials create(String credentialMaterial) {
        if (credentialMaterial == null || credentialMaterial.isBlank()) {
            throw new IllegalArgumentException("Vertex AI provider 必须配置 GCP service account credential material");
        }
        validateServiceAccountEnvelope(credentialMaterial);
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(
                    credentialMaterial.getBytes(StandardCharsets.UTF_8)
            ));
            if (!(credentials instanceof ServiceAccountCredentials)) {
                throw new IllegalArgumentException("Vertex AI credential material 必须是 service_account JSON");
            }
            return credentials.createScopedRequired()
                    ? credentials.createScoped(CLOUD_PLATFORM_SCOPE)
                    : credentials;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Vertex AI credential material 必须是合法 service_account JSON");
        }
    }

    private static void validateServiceAccountEnvelope(String credentialMaterial) {
        Map<String, Object> values;
        try {
            values = OBJECT_MAPPER.readValue(credentialMaterial, MAP_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Vertex AI credential material 必须是合法 service_account JSON");
        }
        if (values == null || !"service_account".equals(text(values, "type").orElse(null))) {
            throw new IllegalArgumentException("Vertex AI credential material 必须是 service_account JSON");
        }
        String tokenUri = text(values, "token_uri")
                .orElseThrow(VertexAiSpringAiCredentialFactory::invalidTokenUri);
        requireOfficialTokenUri(tokenUri);
    }

    private static void requireOfficialTokenUri(String tokenUri) {
        URI uri;
        try {
            uri = new URI(tokenUri);
        } catch (URISyntaxException exception) {
            throw invalidTokenUri();
        }
        int port = uri.getPort();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getRawUserInfo() != null
                || uri.getHost() == null
                || !OFFICIAL_TOKEN_HOST.equalsIgnoreCase(uri.getHost())
                || (port != -1 && port != 443)
                || !OFFICIAL_TOKEN_PATH.equals(uri.getRawPath())
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw invalidTokenUri();
        }
    }

    private static IllegalArgumentException invalidTokenUri() {
        return new IllegalArgumentException(
                "Vertex AI service_account token_uri 必须是官方 HTTPS Google OAuth token endpoint"
        );
    }

    private static Optional<String> text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(text.trim());
        }
        return Optional.empty();
    }

    public static Optional<String> projectId(String credentialMaterial) {
        if (credentialMaterial == null || credentialMaterial.isBlank()) {
            return Optional.empty();
        }
        try {
            GoogleCredentials credentials = create(credentialMaterial);
            if (credentials instanceof ServiceAccountCredentials serviceAccountCredentials
                    && serviceAccountCredentials.getProjectId() != null
                    && !serviceAccountCredentials.getProjectId().isBlank()) {
                return Optional.of(serviceAccountCredentials.getProjectId().trim());
            }
            return Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}

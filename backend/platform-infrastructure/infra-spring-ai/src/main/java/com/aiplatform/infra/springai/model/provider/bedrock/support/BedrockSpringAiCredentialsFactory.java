package com.aiplatform.infra.springai.model.provider.bedrock.support;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * 将受控 credential material 转为 AWS SDK 凭证对象，禁止从普通 options 读取云厂商密钥。
 */
public final class BedrockSpringAiCredentialsFactory {
    private static final JsonMapper OBJECT_MAPPER = new JsonMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private BedrockSpringAiCredentialsFactory() {
    }

    public static AwsCredentialsProvider create(String credentialMaterial) {
        Map<String, Object> credential = credentialMap(credentialMaterial);
        String accessKeyId = requiredText(credential, "accessKeyId");
        String secretAccessKey = requiredText(credential, "secretAccessKey");
        Optional<String> sessionToken = text(credential, "sessionToken");
        AwsCredentials awsCredentials = sessionToken
                .<AwsCredentials>map(token -> AwsSessionCredentials.create(accessKeyId, secretAccessKey, token))
                .orElseGet(() -> AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        return StaticCredentialsProvider.create(awsCredentials);
    }

    public static Map<String, Object> credentialMap(String credentialMaterial) {
        if (credentialMaterial == null || credentialMaterial.isBlank()) {
            throw new IllegalArgumentException("Amazon Bedrock provider 必须配置 AWS credential material");
        }
        try {
            Map<String, Object> values = OBJECT_MAPPER.readValue(credentialMaterial, MAP_TYPE);
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("Amazon Bedrock credential material 必须是 JSON 对象");
            }
            return values;
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Amazon Bedrock credential material 必须是 JSON 对象");
        }
    }

    private static String requiredText(Map<String, Object> values, String key) {
        return text(values, key)
                .orElseThrow(() -> new IllegalArgumentException("Amazon Bedrock credential material 缺少 " + key));
    }

    private static Optional<String> text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(text.trim());
        }
        return Optional.empty();
    }
}

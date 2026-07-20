package com.aiplatform.infra.objectstorage.gateway.minio.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectStoragePropertiesTest {
    private static final String VALID_ACCESS_KEY = "minio-access-2026";
    private static final String VALID_SECRET_KEY = "q".repeat(32);

    @Test
    void normalizesConfiguredObjectStorageFacts() {
        ObjectStorageProperties properties = new ObjectStorageProperties();

        properties.setEndpoint(" http://localhost:9000 ");
        properties.setAccessKey(" " + VALID_ACCESS_KEY + " ");
        properties.setSecretKey(" " + VALID_SECRET_KEY + " ");
        properties.setBucket(" aip-knowledge ");
        properties.setRegion(" us-east-1 ");

        assertThat(properties.getEndpoint()).isEqualTo("http://localhost:9000");
        assertThat(properties.getAccessKey()).isEqualTo(VALID_ACCESS_KEY);
        assertThat(properties.getSecretKey()).isEqualTo(VALID_SECRET_KEY);
        assertThat(properties.getBucket()).isEqualTo("aip-knowledge");
        assertThat(properties.getRegion()).isEqualTo("us-east-1");
    }

    @Test
    void missingObjectStorageCredentialsFailBeanValidation() {
        ObjectStorageProperties properties = new ObjectStorageProperties();

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("accessKey", "secretKey");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"aipminio", "local-dev-access-key", "replace-object-storage-access-key", "changeme"})
    void rejectsKnownWeakOrPlaceholderAccessKeys(String value) {
        ObjectStorageProperties properties = new ObjectStorageProperties();

        assertThatThrownBy(() -> properties.setAccessKey(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessKey不能使用占位或已知弱值");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "aipminio123",
            "local-dev-object-storage-secret",
            "replace-object-storage-secret-key",
            "password-with-known-weak-prefix"
    })
    void rejectsKnownWeakOrPlaceholderSecretKeys(String value) {
        ObjectStorageProperties properties = new ObjectStorageProperties();

        assertThatThrownBy(() -> properties.setSecretKey(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secretKey不能使用占位或已知弱值");
    }

    @Test
    void rejectsBlankConfiguredObjectStorageFacts() {
        ObjectStorageProperties properties = new ObjectStorageProperties();

        assertThatThrownBy(() -> properties.setBucket(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket不能为空白");
    }

    @Test
    void rejectsMissingConfiguredObjectStorageFactsWithChineseMessages() {
        ObjectStorageProperties properties = new ObjectStorageProperties();

        assertThatThrownBy(() -> properties.setEndpoint(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint不能为空");
        assertThatThrownBy(() -> properties.setAccessKey(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessKey不能为空");
        assertThatThrownBy(() -> properties.setSecretKey(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secretKey不能为空");
        assertThatThrownBy(() -> properties.setRegion(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("region不能为空");
    }
}

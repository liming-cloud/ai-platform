package com.aiplatform.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class ProductionSecretConfigurationTest {
    @Test
    void productionSecretsHaveNoFallbackAndModelKeyReferenceIsExplicit() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.datasource.password"))
                .isEqualTo("${AIP_DATASOURCE_PASSWORD}");
        assertThat(properties.getProperty("aip.identity.invitation-signing-key"))
                .isEqualTo("${AIP_IDENTITY_INVITATION_SIGNING_KEY}");
        assertThat(properties.getProperty("aip.identity.invitation-outbox.kms-key-id"))
                .isEqualTo("${AIP_IDENTITY_INVITATION_OUTBOX_KMS_KEY_ID}");
        assertThat(properties.getProperty("aip.identity.invitation-outbox.encryption-key"))
                .isEqualTo("${AIP_IDENTITY_INVITATION_OUTBOX_ENCRYPTION_KEY}");
        assertThat(properties.getProperty("aip.identity.invitation-outbox.previous-keys"))
                .isEqualTo("${AIP_IDENTITY_INVITATION_OUTBOX_PREVIOUS_KEYS:}");
        assertThat(properties.getProperty("aip.object-storage.access-key"))
                .isEqualTo("${AIP_OBJECT_STORAGE_ACCESS_KEY}");
        assertThat(properties.getProperty("aip.object-storage.secret-key"))
                .isEqualTo("${AIP_OBJECT_STORAGE_SECRET_KEY}");
        assertThat(properties.getProperty("aip.model-hub.secret.kms-key-id"))
                .isEqualTo("${AIP_MODEL_HUB_SECRET_KMS_KEY_ID}");
        assertThat(properties.getProperty("aip.model-hub.secret.encryption-key"))
                .isEqualTo("${AIP_MODEL_HUB_SECRET_ENCRYPTION_KEY}");
    }
}

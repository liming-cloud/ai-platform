package com.aiplatform.modelhub.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MdlProviderPersistenceEntityTest {
    @Test
    void providerEntityMapsDatabaseFieldsToDomainWithoutPlainSecret() {
        MdlProviderPersistenceEntity entity = activeProviderEntity();

        ModelProvider provider = entity.toDomain(new ModelSecretRef("9001", "sk-***123"));

        assertThat(provider.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(provider.providerId()).isEqualTo("3001");
        assertThat(provider.providerCode()).isEqualTo("openai");
        assertThat(provider.authType()).isEqualTo("api_key");
        assertThat(provider.endpoint()).isEqualTo("https://api.openai.com/v1");
        assertThat(provider.secretRef().maskedValue()).isEqualTo("sk-***123");
        assertThat(provider.status()).isEqualTo(ModelProviderStatus.ACTIVE);
        assertThat(provider.version()).isEqualTo(3);
    }

    @Test
    void providerEntityRejectsMissingDatabaseIdsInsteadOfStringValueNull() {
        MdlProviderPersistenceEntity entity = activeProviderEntity();
        entity.setTenantId(null);

        assertThatThrownBy(() -> entity.toDomain(new ModelSecretRef("9001", "sk-***")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型供应商租户 ID不能为空");

        entity.setTenantId(2001L);
        entity.setId(null);
        assertThatThrownBy(() -> entity.toDomain(new ModelSecretRef("9001", "sk-***")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型供应商 ID不能为空");
    }

    @Test
    void providerEntityMapsDomainToDatabaseFields() {
        ModelProvider provider = new ModelProvider(
                TenantId.of("2001"),
                "3001",
                "openai",
                "OpenAI",
                "https://api.openai.com/v1",
                "api_key",
                new ModelSecretRef("9001", "sk-***123"),
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-22T00:00:00Z"),
                Instant.parse("2026-05-22T01:00:00Z")
        );

        MdlProviderPersistenceEntity entity = MdlProviderPersistenceEntity.fromDomain(provider);

        assertThat(entity.getId()).isEqualTo(3001L);
        assertThat(entity.getTenantId()).isEqualTo(2001L);
        assertThat(entity.getProviderCode()).isEqualTo("openai");
        assertThat(entity.getProviderName()).isEqualTo("OpenAI");
        assertThat(entity.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(entity.getAuthType()).isEqualTo("api_key");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getApprovalRequestId()).isEqualTo("REQ-TEST");
        assertThat(entity.getVersion()).isZero();
    }

    @Test
    void providerEntityRejectsNonNumericProviderIdWithChineseMessage() {
        ModelProvider provider = new ModelProvider(
                TenantId.of("2001"),
                "openai",
                "openai",
                "OpenAI",
                "https://api.openai.com/v1",
                "api_key",
                new ModelSecretRef("9001", "sk-***123"),
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-22T00:00:00Z"),
                Instant.parse("2026-05-22T01:00:00Z")
        );

        assertThatThrownBy(() -> MdlProviderPersistenceEntity.fromDomain(provider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型供应商 ID 必须是数字型数据库主键");
    }

    private static MdlProviderPersistenceEntity activeProviderEntity() {
        MdlProviderPersistenceEntity entity = new MdlProviderPersistenceEntity();
        entity.setId(3001L);
        entity.setTenantId(2001L);
        entity.setProviderCode("openai");
        entity.setProviderName("OpenAI");
        entity.setBaseUrl("https://api.openai.com/v1");
        entity.setAuthType("api_key");
        entity.setStatus("ACTIVE");
        entity.setVersion(3);
        entity.setCreatedAt(Instant.parse("2026-05-22T00:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-05-22T01:00:00Z"));
        return entity;
    }
}

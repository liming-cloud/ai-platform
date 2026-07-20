package com.aiplatform.modelhub.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.shared.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MdlConfigPersistenceEntityTest {
    @Test
    void configEntityMapsRequiredDatabaseIdsToDomain() {
        MdlConfigPersistenceEntity entity = activeConfigEntity();

        ModelConfig config = entity.toDomain(runtimeOptions());

        assertThat(config.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(config.modelConfigId()).isEqualTo("4001");
        assertThat(config.providerId()).isEqualTo("3001");
        assertThat(config.modelCode()).isEqualTo("gpt-4.1-mini");
        assertThat(config.version()).isEqualTo(4);
    }

    @Test
    void configEntityRejectsMissingDatabaseIdsInsteadOfStringValueNull() {
        MdlConfigPersistenceEntity entity = activeConfigEntity();
        entity.setTenantId(null);

        assertThatThrownBy(() -> entity.toDomain(runtimeOptions()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型配置租户 ID不能为空");

        entity.setTenantId(2001L);
        entity.setId(null);
        assertThatThrownBy(() -> entity.toDomain(runtimeOptions()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型配置 ID不能为空");

        entity.setId(4001L);
        entity.setProviderId(null);
        assertThatThrownBy(() -> entity.toDomain(runtimeOptions()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型配置供应商 ID不能为空");
    }

    @Test
    void configEntityRejectsTestedConnectivityWithoutMessageInsteadOfEmptyFallback() {
        MdlConfigPersistenceEntity entity = activeConfigEntity();
        entity.setConnectivityStatus("FAILED");
        entity.setConnectivityCheckedAt(Instant.parse("2026-05-25T02:01:00Z"));
        entity.setConnectivityMessage(null);

        assertThatThrownBy(() -> entity.toDomain(runtimeOptions()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型连通性说明不能为空");
    }

    @Test
    void configEntityRejectsNotTestedConnectivityWithCheckedAtOrMessage() {
        MdlConfigPersistenceEntity entity = activeConfigEntity();
        entity.setConnectivityCheckedAt(Instant.parse("2026-05-25T02:01:00Z"));

        assertThatThrownBy(() -> entity.toDomain(runtimeOptions()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未测试模型配置不得携带连通性检查时间");

        entity.setConnectivityCheckedAt(null);
        entity.setConnectivityMessage("模型供应商网络连接失败");
        assertThatThrownBy(() -> entity.toDomain(runtimeOptions()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未测试模型配置不得携带连通性说明");
    }

    private static ModelRuntimeOptions runtimeOptions() {
        return new ModelRuntimeOptions(Map.of("providerKind", "openai-compatible"));
    }

    private static MdlConfigPersistenceEntity activeConfigEntity() {
        MdlConfigPersistenceEntity row = new MdlConfigPersistenceEntity();
        row.setId(4001L);
        row.setTenantId(2001L);
        row.setProviderId(3001L);
        row.setModelCode("gpt-4.1-mini");
        row.setModelName("GPT 4.1 Mini");
        row.setModelType("CHAT");
        row.setContextWindow(128000);
        row.setInputPricePer1k(BigDecimal.ZERO);
        row.setOutputPricePer1k(BigDecimal.ZERO);
        row.setOptionsJson("{\"providerKind\":\"openai-compatible\"}");
        row.setConnectivityStatus("NOT_TESTED");
        row.setStatus("ACTIVE");
        row.setVersion(4);
        row.setCreatedAt(Instant.parse("2026-05-25T02:00:00Z"));
        row.setUpdatedAt(Instant.parse("2026-05-25T02:00:00Z"));
        return row;
    }
}

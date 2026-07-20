package com.aiplatform.knowledge.infrastructure.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.domain.modelpolicy.KnowledgeModelUsagePolicyProjection;
import com.aiplatform.knowledge.infrastructure.persistence.entity.KbModelUsagePolicyProjectionPersistenceEntity;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class KbModelUsagePolicyProjectionPersistenceConverterTest {
    private final KbModelUsagePolicyProjectionPersistenceConverter converter =
            new KbModelUsagePolicyProjectionPersistenceConverter(new JsonMapper());

    @Test
    void toDomainPreservesExplicitZeroTokenLimit() {
        KnowledgeModelUsagePolicyProjection projection = converter.toDomain(validRow());

        assertThat(projection.maxInputTokens()).isEqualTo(8192);
        assertThat(projection.maxOutputTokens()).isZero();
        assertThat(projection.providerId()).isEqualTo("3001");
        assertThat(projection.policyRevision()).isEqualTo(17L);
        assertThat(projection.policyDigest()).isEqualTo("a".repeat(64));
        assertThat(projection.allowedKnowledgeBaseIds()).isEmpty();
        assertThat(projection.sourceEventVersion()).isEqualTo(1L);
        assertThat(projection.enabled()).isTrue();
    }

    @Test
    void toDomainRejectsMissingRequiredPolicyFieldsInsteadOfDefaultingThem() {
        assertMissingPolicyField(row -> row.setAllowedKnowledgeBaseIds(null), "允许知识库范围");
        assertMissingPolicyField(row -> row.setEffectiveOptionsJson(null), "模型 options JSON");
        assertMissingPolicyField(row -> row.setMaxInputTokens(null), "输入 Token 上限");
        assertMissingPolicyField(row -> row.setMaxOutputTokens(null), "输出 Token 上限");
        assertMissingPolicyField(row -> row.setProviderId(null), "供应商 ID");
        assertMissingPolicyField(row -> row.setPolicyRevision(null), "策略修订号");
        assertMissingPolicyField(row -> row.setPolicyDigest(null), "策略摘要");
        assertMissingPolicyField(row -> row.setEnabled(null), "策略启用状态");
        assertMissingPolicyField(row -> row.setSourceEventVersion(null), "来源事件版本");
        assertMissingPolicyField(row -> row.setLastEventAt(null), "最后事件时间");
    }

    @Test
    void toDomainRejectsMissingMaskedSecretInsteadOfPlaceholder() {
        KbModelUsagePolicyProjectionPersistenceEntity row = validRow();
        row.setSecretMaskedValue(null);

        assertThatThrownBy(() -> converter.toDomain(row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型供应商凭证材料脱敏值不能为空");
    }

    @Test
    void toDomainRejectsBlankCapabilitiesInsteadOfEmptyPolicyFact() {
        KbModelUsagePolicyProjectionPersistenceEntity row = validRow();
        row.setCapabilities(" ");

        assertThatThrownBy(() -> converter.toDomain(row))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型策略投影持久化记录缺少模型能力");
    }

    @Test
    void toDomainRejectsBlankAllowedUseCasesInsteadOfEmptyPolicyFact() {
        KbModelUsagePolicyProjectionPersistenceEntity row = validRow();
        row.setAllowedUseCases("");

        assertThatThrownBy(() -> converter.toDomain(row))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型策略投影持久化记录缺少允许使用场景");
    }

    @Test
    void toDomainRejectsBlankPolicySetFragmentsInsteadOfFilteringThem() {
        KbModelUsagePolicyProjectionPersistenceEntity row = validRow();
        row.setAllowedKnowledgeBaseIds("7001, ");

        assertThatThrownBy(() -> converter.toDomain(row))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型策略投影持久化记录缺少允许知识库范围");
    }

    @Test
    void toDomainNormalizesPersistenceTextBeforePolicyProjectionRestoration() {
        KbModelUsagePolicyProjectionPersistenceEntity row = validRow();
        row.setModelConfigId(" 4001 ");
        row.setProviderCode(" silicon-flow ");
        row.setProviderKind(" openai-compatible ");
        row.setProviderEndpoint(" https://api.siliconflow.cn/v1 ");
        row.setAuthType(" api_key ");
        row.setSecretRefId(" 9001 ");
        row.setSecretMaskedValue(" ****** ");
        row.setModelCode(" BAAI/bge-m3 ");
        row.setPolicyVersion(" v1 ");
        row.setSourceEventId(" event-001 ");

        KnowledgeModelUsagePolicyProjection projection = converter.toDomain(row);

        assertThat(projection.modelConfigId()).isEqualTo("4001");
        assertThat(projection.providerCode()).isEqualTo("silicon-flow");
        assertThat(projection.providerKind()).isEqualTo("openai-compatible");
        assertThat(projection.connectionRef().endpoint()).isEqualTo("https://api.siliconflow.cn/v1");
        assertThat(projection.connectionRef().authType()).isEqualTo("api_key");
        assertThat(projection.connectionRef().secretRefId()).isEqualTo("9001");
        assertThat(projection.connectionRef().secretMaskedValue()).isEqualTo("******");
        assertThat(projection.modelCode()).isEqualTo("BAAI/bge-m3");
        assertThat(projection.policyVersion()).isEqualTo("v1");
        assertThat(projection.sourceEventId()).isEqualTo("event-001");
    }

    @Test
    void toEntityRejectsExistingProjectionWithoutVersionInsteadOfZeroFallback() {
        KnowledgeModelUsagePolicyProjection projection = converter.toDomain(validRow());

        assertThatThrownBy(() -> converter.toEntity(projection, 1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型策略投影持久化记录缺少乐观锁版本");

        assertThat(converter.toEntity(projection, null, null).getVersion()).isZero();
    }

    private void assertMissingPolicyField(
            Consumer<KbModelUsagePolicyProjectionPersistenceEntity> mutation,
            String fieldName
    ) {
        KbModelUsagePolicyProjectionPersistenceEntity row = validRow();
        mutation.accept(row);

        assertThatThrownBy(() -> converter.toDomain(row))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型策略投影持久化记录缺少" + fieldName);
    }

    private static KbModelUsagePolicyProjectionPersistenceEntity validRow() {
        KbModelUsagePolicyProjectionPersistenceEntity row = new KbModelUsagePolicyProjectionPersistenceEntity();
        row.setId(1L);
        row.setTenantId(2001L);
        row.setModelConfigId("4001");
        row.setProviderId(3001L);
        row.setProviderCode("silicon-flow");
        row.setProviderKind("openai-compatible");
        row.setProviderEndpoint("https://api.siliconflow.cn/v1");
        row.setAuthType("api_key");
        row.setSecretRefId("9001");
        row.setSecretMaskedValue("******");
        row.setModelCode("BAAI/bge-m3");
        row.setModelConfigVersion(7);
        row.setModelType("EMBEDDING");
        row.setCapabilities("EMBEDDING");
        row.setAllowedUseCases("RAG_INDEXING,RAG_QUERY");
        row.setAllowedKnowledgeBaseIds("");
        row.setEffectiveOptionsJson("{\"dimensions\":1536}");
        row.setEffectiveOptionsDigest("a".repeat(64));
        row.setMaxInputTokens(8192);
        row.setMaxOutputTokens(0);
        row.setPolicyVersion("v1");
        row.setPolicyRevision(17L);
        row.setPolicyDigest("a".repeat(64));
        row.setEnabled(true);
        row.setSourceEventId("event-001");
        row.setSourceEventVersion(1L);
        row.setLastEventAt(Instant.parse("2026-05-25T08:00:00Z"));
        row.setCreatedAt(Instant.parse("2026-05-25T08:00:00Z"));
        row.setUpdatedAt(Instant.parse("2026-05-25T08:00:00Z"));
        row.setVersion(0);
        return row;
    }
}

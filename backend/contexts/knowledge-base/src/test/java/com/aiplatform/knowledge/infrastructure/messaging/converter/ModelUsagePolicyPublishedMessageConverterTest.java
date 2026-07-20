package com.aiplatform.knowledge.infrastructure.messaging.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.knowledge.domain.modelpolicy.KnowledgeModelUsagePolicyProjection;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ModelUsagePolicyPublishedMessageConverterTest {
    private final ModelUsagePolicyPublishedMessageConverter converter =
            new ModelUsagePolicyPublishedMessageConverter(new JsonMapper());

    @Test
    void toProjectionPreservesExplicitZeroTokenLimit() {
        KnowledgeModelUsagePolicyProjection projection = converter.toProjection(message("""
                {
                  "modelConfigId": "4001",
                  "providerId": "3001",
                  "providerCode": "silicon-flow",
                  "providerKind": "openai-compatible",
                  "providerEndpoint": "https://api.siliconflow.cn/v1",
                  "authType": "api_key",
                  "secretRefId": "9001",
                  "secretMaskedValue": "******",
                  "modelCode": "BAAI/bge-m3",
                  "modelConfigVersion": 7,
                  "modelType": "EMBEDDING",
                  "capabilities": ["EMBEDDING"],
                  "allowedUseCases": ["RAG_INDEXING", "RAG_QUERY"],
                  "allowedKnowledgeBaseIds": [],
                  "effectiveOptions": {"dimensions": 1536},
                  "effectiveOptionsDigest": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "maxInputTokens": 8192,
                  "maxOutputTokens": 0,
                  "policyVersion": "v1",
                  "policyRevision": 1,
                  "policyDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "enabled": true
                }
                """));

        assertThat(projection.maxInputTokens()).isEqualTo(8192);
        assertThat(projection.maxOutputTokens()).isZero();
        assertThat(projection.allowedKnowledgeBaseIds()).isEmpty();
        assertThat(projection.enabled()).isTrue();
        assertThat(projection.modelConfigVersion()).isEqualTo(7);
        assertThat(projection.modelType()).isEqualTo("EMBEDDING");
        assertThat(projection.effectiveOptionsDigest()).isEqualTo("b".repeat(64));
    }

    @Test
    void toProjectionRejectsMissingTokenLimitsInsteadOfDefaultingToZero() {
        assertThatThrownBy(() -> converter.toProjection(message("""
                {
                  "modelConfigId": "4001",
                  "providerId": "3001",
                  "providerCode": "silicon-flow",
                  "providerKind": "openai-compatible",
                  "providerEndpoint": "https://api.siliconflow.cn/v1",
                  "authType": "api_key",
                  "secretRefId": "9001",
                  "secretMaskedValue": "******",
                  "modelCode": "BAAI/bge-m3",
                  "modelConfigVersion": 7,
                  "modelType": "EMBEDDING",
                  "capabilities": ["EMBEDDING"],
                  "allowedUseCases": ["RAG_INDEXING", "RAG_QUERY"],
                  "allowedKnowledgeBaseIds": [],
                  "effectiveOptions": {"dimensions": 1536},
                  "effectiveOptionsDigest": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "maxOutputTokens": 0,
                  "policyVersion": "v1",
                  "policyRevision": 1,
                  "policyDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "enabled": true
                }
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("输入 Token 上限不能为空");
    }

    @Test
    void toProjectionRejectsMissingAllowedKnowledgeBaseIdsInsteadOfGrantingAll() {
        assertThatThrownBy(() -> converter.toProjection(message("""
                {
                  "modelConfigId": "4001",
                  "providerId": "3001",
                  "providerCode": "silicon-flow",
                  "providerKind": "openai-compatible",
                  "providerEndpoint": "https://api.siliconflow.cn/v1",
                  "authType": "api_key",
                  "secretRefId": "9001",
                  "secretMaskedValue": "******",
                  "modelCode": "BAAI/bge-m3",
                  "modelConfigVersion": 7,
                  "modelType": "EMBEDDING",
                  "capabilities": ["EMBEDDING"],
                  "allowedUseCases": ["RAG_INDEXING", "RAG_QUERY"],
                  "effectiveOptions": {"dimensions": 1536},
                  "effectiveOptionsDigest": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "maxInputTokens": 8192,
                  "maxOutputTokens": 0,
                  "policyVersion": "v1",
                  "policyRevision": 1,
                  "policyDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "enabled": true
                }
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("允许知识库范围不能为空");
    }

    @Test
    void toProjectionRejectsMissingMaskedSecretInsteadOfPlaceholder() {
        assertThatThrownBy(() -> converter.toProjection(message("""
                {
                  "modelConfigId": "4001",
                  "providerId": "3001",
                  "providerCode": "silicon-flow",
                  "providerKind": "openai-compatible",
                  "providerEndpoint": "https://api.siliconflow.cn/v1",
                  "authType": "api_key",
                  "secretRefId": "9001",
                  "modelCode": "BAAI/bge-m3",
                  "modelConfigVersion": 7,
                  "modelType": "EMBEDDING",
                  "capabilities": ["EMBEDDING"],
                  "allowedUseCases": ["RAG_INDEXING", "RAG_QUERY"],
                  "allowedKnowledgeBaseIds": [],
                  "effectiveOptions": {"dimensions": 1536},
                  "effectiveOptionsDigest": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "maxInputTokens": 8192,
                  "maxOutputTokens": 0,
                  "policyVersion": "v1",
                  "policyRevision": 1,
                  "policyDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "enabled": true
                }
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型供应商凭证材料脱敏值不能为空");
    }

    @Test
    void toProjectionRejectsEmptyCapabilitiesInsteadOfPolicyDenial() {
        assertThatThrownBy(() -> converter.toProjection(message("""
                {
                  "modelConfigId": "4001",
                  "providerId": "3001",
                  "providerCode": "silicon-flow",
                  "providerKind": "openai-compatible",
                  "providerEndpoint": "https://api.siliconflow.cn/v1",
                  "authType": "api_key",
                  "secretRefId": "9001",
                  "secretMaskedValue": "******",
                  "modelCode": "BAAI/bge-m3",
                  "modelConfigVersion": 7,
                  "modelType": "EMBEDDING",
                  "capabilities": [],
                  "allowedUseCases": ["RAG_INDEXING", "RAG_QUERY"],
                  "allowedKnowledgeBaseIds": [],
                  "effectiveOptions": {"dimensions": 1536},
                  "effectiveOptionsDigest": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "maxInputTokens": 8192,
                  "maxOutputTokens": 0,
                  "policyVersion": "v1",
                  "policyRevision": 1,
                  "policyDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "enabled": true
                }
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型能力不能为空");
    }

    @Test
    void toProjectionRejectsEmptyAllowedUseCasesInsteadOfPolicyDenial() {
        assertThatThrownBy(() -> converter.toProjection(message("""
                {
                  "modelConfigId": "4001",
                  "providerId": "3001",
                  "providerCode": "silicon-flow",
                  "providerKind": "openai-compatible",
                  "providerEndpoint": "https://api.siliconflow.cn/v1",
                  "authType": "api_key",
                  "secretRefId": "9001",
                  "secretMaskedValue": "******",
                  "modelCode": "BAAI/bge-m3",
                  "modelConfigVersion": 7,
                  "modelType": "EMBEDDING",
                  "capabilities": ["EMBEDDING"],
                  "allowedUseCases": [],
                  "allowedKnowledgeBaseIds": [],
                  "effectiveOptions": {"dimensions": 1536},
                  "effectiveOptionsDigest": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "maxInputTokens": 8192,
                  "maxOutputTokens": 0,
                  "policyVersion": "v1",
                  "policyRevision": 1,
                  "policyDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "enabled": true
                }
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("允许使用场景不能为空");
    }

    private static DomainEventMessage message(String payloadJson) {
        return new DomainEventMessage(
                "event-001",
                "ModelUsagePolicyPublished",
                "2001",
                "ModelConfig",
                "4001",
                2,
                "schema/model-usage-policy.v2",
                Instant.parse("2026-05-25T08:00:00Z"),
                "trace-001",
                "model-hub",
                payloadJson
        );
    }
}

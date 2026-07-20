package com.aiplatform.appstudio.infrastructure.messaging.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.appstudio.domain.modelpolicy.AppModelUsagePolicyProjection;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ModelUsagePolicyPublishedMessageConverterTest {
    private final ModelUsagePolicyPublishedMessageConverter converter =
            new ModelUsagePolicyPublishedMessageConverter(new JsonMapper());

    @Test
    void toProjectionPreservesExplicitEmptyAllowedApplicationIdsAsEmptyScope() {
        AppModelUsagePolicyProjection projection = converter.toProjection(message("""
                {
                  "modelConfigId": "4001",
                  "providerId": "3001",
                  "providerCode": "silicon-flow",
                  "providerKind": "openai-compatible",
                  "providerEndpoint": "https://api.siliconflow.cn/v1",
                  "authType": "api_key",
                  "secretRefId": "9001",
                  "modelCode": "deepseek-chat",
                  "modelConfigVersion": 7,
                  "modelType": "CHAT",
                  "capabilities": ["CHAT"],
                  "allowedUseCases": ["RAG_QUERY"],
                  "allowedApplicationIds": [],
                  "effectiveOptions": {"temperature": 0.2},
                  "effectiveOptionsDigest": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "maxInputTokens": 8192,
                  "maxOutputTokens": 2048,
                  "policyVersion": "v1",
                  "policyRevision": 1,
                  "policyDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "enabled": true
                }
                """));

        assertThat(projection.allowedApplicationIds()).isEmpty();
        assertThat(projection.maxOutputTokens()).isEqualTo(2048);
        assertThat(projection.enabled()).isTrue();
        assertThat(projection.modelConfigVersion()).isEqualTo(7);
        assertThat(projection.modelType()).isEqualTo("CHAT");
        assertThat(projection.effectiveOptionsDigest()).isEqualTo("b".repeat(64));
    }

    @Test
    void toProjectionAcceptsTheCompleteV2ProducerPayloadWithoutDependingOnBootJacksonDefaults() {
        AppModelUsagePolicyProjection projection = converter.toProjection(message("""
                {
                  "modelConfigId": "4001",
                  "providerId": "3001",
                  "providerCode": "silicon-flow",
                  "providerKind": "openai-compatible",
                  "providerEndpoint": "https://api.siliconflow.cn/v1",
                  "authType": "api_key",
                  "secretRefId": "9001",
                  "secretMaskedValue": "sk-***",
                  "modelCode": "deepseek-chat",
                  "modelConfigVersion": 7,
                  "modelType": "CHAT",
                  "capabilities": ["CHAT"],
                  "allowedUseCases": ["RAG_QUERY"],
                  "allowedKnowledgeBaseIds": [],
                  "allowedApplicationIds": ["7001"],
                  "effectiveOptions": {"temperature": 0.2},
                  "effectiveOptionsDigest": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "maxInputTokens": 8192,
                  "maxOutputTokens": 2048,
                  "policyVersion": "v1",
                  "policyRevision": 1,
                  "policyDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "enabled": true
                }
                """));

        assertThat(projection.allowedApplicationIds()).containsExactly("7001");
        assertThat(projection.connectionRef().secretRefId()).isEqualTo("9001");
    }

    @Test
    void toProjectionRejectsMissingAllowedApplicationIdsBecauseItWouldOpenScopeSilently() {
        assertThatThrownBy(() -> converter.toProjection(message("""
                {
                  "modelConfigId": "4001",
                  "providerId": "3001",
                  "providerCode": "silicon-flow",
                  "providerKind": "openai-compatible",
                  "providerEndpoint": "https://api.siliconflow.cn/v1",
                  "authType": "api_key",
                  "secretRefId": "9001",
                  "modelCode": "deepseek-chat",
                  "modelConfigVersion": 7,
                  "modelType": "CHAT",
                  "capabilities": ["CHAT"],
                  "allowedUseCases": ["RAG_QUERY"],
                  "effectiveOptions": {"temperature": 0.2},
                  "effectiveOptionsDigest": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "maxInputTokens": 8192,
                  "maxOutputTokens": 2048,
                  "policyVersion": "v1",
                  "policyRevision": 1,
                  "policyDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "enabled": true
                }
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("允许应用范围不能为空");
    }

    @Test
    void toProjectionRejectsMissingTokenLimits() {
        assertThatThrownBy(() -> converter.toProjection(message("""
                {
                  "modelConfigId": "4001",
                  "providerId": "3001",
                  "providerCode": "silicon-flow",
                  "providerKind": "openai-compatible",
                  "providerEndpoint": "https://api.siliconflow.cn/v1",
                  "authType": "api_key",
                  "secretRefId": "9001",
                  "modelCode": "deepseek-chat",
                  "modelConfigVersion": 7,
                  "modelType": "CHAT",
                  "capabilities": ["CHAT"],
                  "allowedUseCases": ["RAG_QUERY"],
                  "allowedApplicationIds": [],
                  "effectiveOptions": {"temperature": 0.2},
                  "effectiveOptionsDigest": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "maxOutputTokens": 2048,
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
    void toProjectionRejectsEmptyCapabilities() {
        assertThatThrownBy(() -> converter.toProjection(message("""
                {
                  "modelConfigId": "4001",
                  "providerId": "3001",
                  "providerCode": "silicon-flow",
                  "providerKind": "openai-compatible",
                  "providerEndpoint": "https://api.siliconflow.cn/v1",
                  "authType": "api_key",
                  "secretRefId": "9001",
                  "modelCode": "deepseek-chat",
                  "modelConfigVersion": 7,
                  "modelType": "CHAT",
                  "capabilities": [],
                  "allowedUseCases": ["RAG_QUERY"],
                  "allowedApplicationIds": [],
                  "effectiveOptions": {"temperature": 0.2},
                  "effectiveOptionsDigest": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "maxInputTokens": 8192,
                  "maxOutputTokens": 2048,
                  "policyVersion": "v1",
                  "policyRevision": 1,
                  "policyDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "enabled": true
                }
                """)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型能力不能为空");
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

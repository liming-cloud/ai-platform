package com.aiplatform.modelhub.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.domain.ModelType;
import com.aiplatform.modelhub.infrastructure.messaging.publisher.RocketmqModelUsagePolicyPublisher;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelRuntimeAuthorityDigestMapper;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelUsagePolicyOutboxMapper;
import com.aiplatform.modelhub.infrastructure.springai.adapter.ModelRuntimeAuthorityDigest;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RocketmqModelUsagePolicyPublisherTest {
    private static final String CANONICAL_POLICY_DIGEST = "c".repeat(64);
    private static final String CANONICAL_OPTIONS_DIGEST = "d".repeat(64);

    @AfterEach
    void cleanupTrace() {
        TraceContext.clear();
    }

    @Test
    void publishesEmbeddingPolicyForKnowledgeBaseProjection() throws Exception {
        TraceContext.ensureTraceId("trace-model-policy");
        ModelUsagePolicyOutboxMapper outbox = acceptingOutbox();
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);

        publisher.publish(provider(), embeddingConfig());

        ArgumentCaptor<DomainEventMessage> captor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(outbox).enqueue(captor.capture());
        DomainEventMessage message = captor.getValue();
        assertThat(message.eventType()).isEqualTo("ModelUsagePolicyPublished");
        assertThat(message.version()).isEqualTo(2L);
        assertThat(message.schemaVersion()).isEqualTo("schema/model-usage-policy.v2");
        assertThat(message.tenantId()).isEqualTo("2001");
        assertThat(message.aggregateType()).isEqualTo("ModelConfig");
        assertThat(message.aggregateId()).isEqualTo("4001");
        assertThat(message.traceId()).isEqualTo("trace-model-policy");
        JsonNode payload = new JsonMapper().readTree(message.payloadJson());
        assertThat(payload.get("modelConfigId").asText()).isEqualTo("4001");
        assertThat(payload.get("providerId").asText()).isEqualTo("3001");
        assertThat(payload.get("providerCode").asText()).isEqualTo("silicon-flow");
        assertThat(payload.get("providerKind").asText()).isEqualTo("openai-compatible");
        assertThat(payload.get("providerEndpoint").asText()).isEqualTo("https://api.siliconflow.cn/v1");
        assertThat(payload.get("authType").asText()).isEqualTo("api_key");
        assertThat(payload.get("secretRefId").asText()).isEqualTo("9001");
        assertThat(payload.get("secretMaskedValue").asText()).isEqualTo("sk-***");
        assertThat(payload.get("modelCode").asText()).isEqualTo("BAAI/bge-m3");
        assertThat(payload.get("modelConfigVersion").asInt()).isZero();
        assertThat(payload.get("modelType").asText()).isEqualTo("EMBEDDING");
        assertThat(payload.get("capabilities").get(0).asText()).isEqualTo("EMBEDDING");
        assertThat(payload.get("allowedUseCases").get(0).asText()).isEqualTo("RAG_INDEXING");
        assertThat(payload.get("allowedKnowledgeBaseIds")).isEmpty();
        assertThat(payload.get("allowedApplicationIds")).isEmpty();
        assertThat(payload.get("effectiveOptions").get("dimensions").asInt()).isEqualTo(1536);
        assertThat(payload.get("effectiveOptions").has("providerKind")).isFalse();
        assertThat(payload.get("effectiveOptionsDigest").asText()).isEqualTo(CANONICAL_OPTIONS_DIGEST);
        assertThat(payload.get("maxInputTokens").asInt()).isEqualTo(8192);
        assertThat(payload.get("policyRevision").asLong()).isEqualTo(1L);
        assertThat(payload.get("policyVersion").asText()).isEqualTo("mdl-4001-r1");
        assertThat(payload.get("policyDigest").asText()).isEqualTo(CANONICAL_POLICY_DIGEST);
        assertThat(payload.get("enabled").asBoolean()).isTrue();
        assertThat(message.payloadJson()).doesNotContain("sk-live");

        ArgumentCaptor<String> unsignedPayloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outbox).canonicalDigest(unsignedPayloadCaptor.capture());
        JsonNode unsignedPayload = new JsonMapper().readTree(unsignedPayloadCaptor.getValue());
        assertThat(unsignedPayload.get("policyDigest").asText()).isEmpty();
    }

    @Test
    void doesNotPublishDedicatedEmbeddingDimensionAsRuntimeDimensionsOption() throws Exception {
        TraceContext.ensureTraceId("trace-model-policy-dedicated-dim");
        ModelUsagePolicyOutboxMapper outbox = acceptingOutbox();
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);

        publisher.publish(provider(), embeddingConfigWithDedicatedDimensionOnly());

        ArgumentCaptor<DomainEventMessage> captor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(outbox).enqueue(captor.capture());
        JsonNode payload = new JsonMapper().readTree(captor.getValue().payloadJson());
        assertThat(payload.get("effectiveOptions").has("dimensions")).isFalse();
        assertThat(payload.get("effectiveOptions").has("providerKind")).isFalse();
    }

    @Test
    void publishesChatPolicyWithRagQueryUseCaseForKnowledgeAnswerGeneration() throws Exception {
        ModelUsagePolicyOutboxMapper outbox = acceptingOutbox();
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);

        publisher.publish(provider(), chatConfig());

        ArgumentCaptor<DomainEventMessage> captor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(outbox).enqueue(captor.capture());
        JsonNode payload = new JsonMapper().readTree(captor.getValue().payloadJson());
        assertThat(payload.get("capabilities").get(0).asText()).isEqualTo("CHAT");
        assertThat(payload.get("allowedUseCases").get(0).asText()).isEqualTo("RAG_QUERY");
        assertThat(payload.get("enabled").asBoolean()).isTrue();
    }

    @Test
    void publishesNoAuthProviderWithoutSecretReference() throws Exception {
        TraceContext.ensureTraceId("trace-model-policy-no-auth");
        ModelUsagePolicyOutboxMapper outbox = acceptingOutbox();
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);

        publisher.publish(noAuthProvider(), noAuthEmbeddingConfig());

        ArgumentCaptor<DomainEventMessage> captor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(outbox).enqueue(captor.capture());
        JsonNode payload = new JsonMapper().readTree(captor.getValue().payloadJson());
        assertThat(payload.get("providerKind").asText()).isEqualTo("ollama");
        assertThat(payload.get("authType").asText()).isEqualTo("no_auth");
        assertThat(payload.get("secretRefId").isNull()).isTrue();
        assertThat(payload.get("secretMaskedValue").isNull()).isTrue();
        assertThat(payload.get("enabled").asBoolean()).isTrue();
    }

    @Test
    void publishesCloudCredentialProviderWithCredentialReference() throws Exception {
        TraceContext.ensureTraceId("trace-model-policy-cloud");
        ModelUsagePolicyOutboxMapper outbox = acceptingOutbox();
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);

        publisher.publish(cloudCredentialProvider(), cloudCredentialEmbeddingConfig());

        ArgumentCaptor<DomainEventMessage> captor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(outbox).enqueue(captor.capture());
        JsonNode payload = new JsonMapper().readTree(captor.getValue().payloadJson());
        assertThat(payload.get("providerKind").asText()).isEqualTo("bedrock");
        assertThat(payload.get("authType").asText()).isEqualTo("aws_credentials");
        assertThat(payload.get("secretRefId").asText()).isEqualTo("9101");
        assertThat(payload.get("secretMaskedValue").asText()).isEqualTo("ak-***tail");
        assertThat(payload.get("enabled").asBoolean()).isTrue();
    }

    @Test
    void publishDisablesActiveConfigWhenConnectivityNotPassed() throws Exception {
        ModelUsagePolicyOutboxMapper outbox = acceptingOutbox();
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);

        publisher.publish(provider(), failedConnectivityConfig());

        ArgumentCaptor<DomainEventMessage> captor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(outbox).enqueue(captor.capture());
        JsonNode payload = new JsonMapper().readTree(captor.getValue().payloadJson());
        assertThat(payload.get("enabled").asBoolean()).isFalse();
    }

    @Test
    void failsClosedBeforeEnqueueWhenTheDatabaseCanonicalDigestIsMalformed() {
        ModelUsagePolicyOutboxMapper outbox = mock(ModelUsagePolicyOutboxMapper.class);
        when(outbox.nextPolicyRevision()).thenReturn(1L);
        when(outbox.canonicalDigest(any(String.class))).thenReturn("A".repeat(64));
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);

        assertThatThrownBy(() -> publisher.publish(provider(), embeddingConfig()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("摘要")
                .hasMessageNotContaining("A".repeat(64));

        verify(outbox, never()).enqueue(any(DomainEventMessage.class));
    }

    @Test
    void rejectsReservedMaximumRevisionBeforeSerializingOrEnqueueing() {
        ModelUsagePolicyOutboxMapper outbox = mock(ModelUsagePolicyOutboxMapper.class);
        when(outbox.nextPolicyRevision()).thenReturn(Long.MAX_VALUE);
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);

        assertThatThrownBy(() -> publisher.publish(provider(), embeddingConfig()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("修订号生成失败");

        verify(outbox, never()).canonicalDigest(any(String.class));
        verify(outbox, never()).enqueue(any(DomainEventMessage.class));
    }

    @Test
    void rejectsUnsupportedAuthTypeBeforePublishingPolicy() {
        ModelUsagePolicyOutboxMapper outbox = acceptingOutbox();
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);

        assertThatThrownBy(() -> publisher.publish(unsupportedAuthProvider(), embeddingConfig()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("暂不支持模型供应商鉴权方式");
    }

    @Test
    void rejectsMissingProviderKindBeforePublishingPolicy() {
        ModelUsagePolicyOutboxMapper outbox = acceptingOutbox();
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);

        assertThatThrownBy(() -> publisher.publish(provider(), embeddingConfigWithoutProviderKind()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerKind");
    }

    @Test
    void rejectsPlaintextSecretOptionsBeforeWritingOutbox() {
        ModelUsagePolicyOutboxMapper outbox = acceptingOutbox();
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);

        assertThatThrownBy(() -> publisher.publish(provider(), embeddingConfigWithPlaintextSecret()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("秘密字段");

        verify(outbox, never()).enqueue(any(DomainEventMessage.class));
    }

    @Test
    void rejectsSecretMaterialInNestedOptionValuesWithoutEchoingIt() {
        ModelUsagePolicyOutboxMapper outbox = acceptingOutbox();
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);
        Map<String, Object> unsafeValues = new LinkedHashMap<>();
        unsafeValues.put("Bearer sk-value-must-not-leak", "Bearer sk-value-must-not-leak");
        unsafeValues.put("api_key=value-must-not-leak", List.of("safe-stop", "api_key=value-must-not-leak"));
        unsafeValues.put("form-bearer-must-not-leak", List.of("Bearer+form-bearer-must-not-leak"));
        unsafeValues.put(
                "private-key-value-must-not-leak",
                Set.of("-----BEGIN PRIVATE KEY-----\nprivate-key-value-must-not-leak\n-----END PRIVATE KEY-----")
        );
        unsafeValues.put(
                "nested-credential-must-not-leak",
                new Object[]{Map.of("note", "credential=nested-credential-must-not-leak")}
        );
        unsafeValues.put(
                "encoded-credential-must-not-leak",
                List.of(Base64.getEncoder().encodeToString(
                        "authorization=Bearer encoded-credential-must-not-leak".getBytes(StandardCharsets.UTF_8)
                ))
        );

        unsafeValues.forEach((secret, unsafeValue) -> {
            assertThatThrownBy(() -> publisher.publish(
                    provider(),
                    chatConfigWithOptions(Map.of(
                            "providerKind", "openai-compatible",
                            "stopSequences", unsafeValue
                    ))
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("秘密")
                    .hasMessageNotContaining(secret);
        });

        verify(outbox, never()).enqueue(any(DomainEventMessage.class));
    }

    @Test
    void rejectsSecretMaterialUsedAsAnOptionMapKeyWithoutEchoingIt() {
        ModelUsagePolicyOutboxMapper outbox = acceptingOutbox();
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);
        String secretKey = "Bearer key-material-must-not-leak";
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("providerKind", "openai-compatible");
        options.put(secretKey, "safe-value");

        assertThatThrownBy(() -> publisher.publish(provider(), chatConfigWithOptions(options)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("秘密")
                .hasMessageNotContaining(secretKey);

        verify(outbox, never()).enqueue(any(DomainEventMessage.class));
    }

    @Test
    void preservesOrdinaryStopSequencesWhenPublishingPolicy() throws Exception {
        ModelUsagePolicyOutboxMapper outbox = acceptingOutbox();
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);

        publisher.publish(provider(), chatConfigWithOptions(Map.of(
                "providerKind", "openai-compatible",
                "stopSequences", List.of("END", "###", "assistant:", "Stop1234", "Bearer", "C++")
        )));

        ArgumentCaptor<DomainEventMessage> captor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(outbox).enqueue(captor.capture());
        JsonNode stopSequences = new JsonMapper().readTree(captor.getValue().payloadJson())
                .get("effectiveOptions")
                .get("stopSequences");
        assertThat(stopSequences).hasSize(6);
        assertThat(stopSequences.get(0).asText()).isEqualTo("END");
        assertThat(stopSequences.get(1).asText()).isEqualTo("###");
        assertThat(stopSequences.get(2).asText()).isEqualTo("assistant:");
        assertThat(stopSequences.get(3).asText()).isEqualTo("Stop1234");
        assertThat(stopSequences.get(4).asText()).isEqualTo("Bearer");
        assertThat(stopSequences.get(5).asText()).isEqualTo("C++");
    }

    @Test
    void rejectsCredentialBearingProviderEndpointBeforeWritingOutbox() {
        ModelUsagePolicyOutboxMapper outbox = acceptingOutbox();
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);

        assertThatThrownBy(() -> publisher.publish(providerWithCredentialEndpoint(), embeddingConfig()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("访问地址不能携带认证材料");

        verify(outbox, never()).enqueue(any(DomainEventMessage.class));
    }

    @Test
    void rejectsSignedProviderEndpointBeforeWritingOutbox() {
        ModelUsagePolicyOutboxMapper outbox = acceptingOutbox();
        RocketmqModelUsagePolicyPublisher publisher = publisher(outbox);

        assertThatThrownBy(() -> publisher.publish(providerWithSignedEndpoint(), embeddingConfig()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("访问地址不能携带认证材料");

        verify(outbox, never()).enqueue(any(DomainEventMessage.class));
    }

    private static ModelUsagePolicyOutboxMapper acceptingOutbox() {
        ModelUsagePolicyOutboxMapper outbox = mock(ModelUsagePolicyOutboxMapper.class);
        when(outbox.nextPolicyRevision()).thenReturn(1L);
        when(outbox.canonicalDigest(any(String.class))).thenReturn(CANONICAL_POLICY_DIGEST);
        when(outbox.enqueue(any(DomainEventMessage.class))).thenReturn(1);
        return outbox;
    }

    private static RocketmqModelUsagePolicyPublisher publisher(ModelUsagePolicyOutboxMapper outbox) {
        ModelRuntimeAuthorityDigestMapper digestMapper = mock(ModelRuntimeAuthorityDigestMapper.class);
        when(digestMapper.canonicalEffectiveOptionsDigest(any(String.class)))
                .thenReturn(CANONICAL_OPTIONS_DIGEST);
        return new RocketmqModelUsagePolicyPublisher(
                outbox,
                new JsonMapper(),
                new ModelRuntimeAuthorityDigest(digestMapper, new JsonMapper())
        );
    }

    private static ModelProvider provider() {
        return new ModelProvider(
                TenantId.of("2001"),
                "3001",
                "silicon-flow",
                "硅基流动",
                "https://api.siliconflow.cn/v1",
                "api_key",
                new ModelSecretRef("9001", "sk-***"),
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                false,
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T07:00:00Z")
        );
    }

    private static ModelProvider noAuthProvider() {
        return new ModelProvider(
                TenantId.of("2001"),
                "3002",
                "local-ollama",
                "本地 Ollama",
                "http://localhost:11434",
                "no_auth",
                null,
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                false,
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T07:00:00Z")
        );
    }

    private static ModelProvider cloudCredentialProvider() {
        return new ModelProvider(
                TenantId.of("2001"),
                "3003",
                "aws-bedrock",
                "AWS Bedrock",
                "https://bedrock-runtime.us-east-1.amazonaws.com",
                "aws_credentials",
                new ModelSecretRef("9101", "ak-***tail"),
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                false,
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T07:00:00Z")
        );
    }

    private static ModelProvider unsupportedAuthProvider() {
        return new ModelProvider(
                TenantId.of("2001"),
                "3003",
                "custom-provider",
                "自定义供应商",
                "https://api.example/v1",
                "oauth2",
                new ModelSecretRef("UNUSED", "unused-***mask"),
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                false,
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T07:00:00Z")
        );
    }

    private static ModelProvider providerWithCredentialEndpoint() {
        return new ModelProvider(
                TenantId.of("2001"),
                "3001",
                "unsafe-endpoint-provider",
                "Unsafe endpoint provider",
                "https://api-user:sk-live-must-not-enter-outbox@example.com/v1",
                "api_key",
                new ModelSecretRef("9001", "sk-***"),
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                false,
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T07:00:00Z")
        );
    }

    private static ModelProvider providerWithSignedEndpoint() {
        return new ModelProvider(
                TenantId.of("2001"),
                "3001",
                "signed-endpoint-provider",
                "Signed endpoint provider",
                "https://api.example.com/v1?X-Amz-Signature=must-not-enter-outbox",
                "api_key",
                new ModelSecretRef("9001", "sk-***"),
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                false,
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T07:00:00Z")
        );
    }

    private static ModelConfig cloudCredentialEmbeddingConfig() {
        return new ModelConfig(
                TenantId.of("2001"),
                "4003",
                "3003",
                "amazon.titan-embed-text-v2:0",
                "Titan Embed Text",
                ModelType.EMBEDDING,
                8192,
                1024,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of(
                        "providerKind", "bedrock",
                        "dimensions", 1024
                )),
                ModelConnectivityStatus.PASSED,
                Instant.parse("2026-05-25T07:30:00Z"),
                "连接成功",
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T08:00:00Z")
        );
    }

    private static ModelConfig embeddingConfig() {
        return new ModelConfig(
                TenantId.of("2001"),
                "4001",
                "3001",
                "BAAI/bge-m3",
                "BGE M3",
                ModelType.EMBEDDING,
                8192,
                1536,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of(
                        "providerKind", "openai-compatible",
                        "dimensions", 1536
                )),
                ModelConnectivityStatus.PASSED,
                Instant.parse("2026-05-25T07:30:00Z"),
                "连接成功",
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T08:00:00Z")
        );
    }

    private static ModelConfig failedConnectivityConfig() {
        return new ModelConfig(
                TenantId.of("2001"),
                "4005",
                "3001",
                "BAAI/bge-m3",
                "BGE M3",
                ModelType.EMBEDDING,
                8192,
                1536,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of(
                        "providerKind", "openai-compatible",
                        "dimensions", 1536
                )),
                ModelConnectivityStatus.FAILED,
                Instant.parse("2026-05-25T07:30:00Z"),
                "模型供应商网络连接失败",
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T08:00:00Z")
        );
    }

    private static ModelConfig embeddingConfigWithDedicatedDimensionOnly() {
        return new ModelConfig(
                TenantId.of("2001"),
                "4010",
                "3001",
                "text-embedding-ui-dedicated-dim",
                "UI Dedicated Dimension",
                ModelType.EMBEDDING,
                8192,
                1536,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of(
                        "providerKind", "openai-compatible"
                )),
                ModelConnectivityStatus.PASSED,
                Instant.parse("2026-05-25T07:30:00Z"),
                "连接成功",
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T08:00:00Z")
        );
    }

    private static ModelConfig chatConfig() {
        return new ModelConfig(
                TenantId.of("2001"),
                "4005",
                "3001",
                "gpt-4.1-mini",
                "GPT-4.1 mini",
                ModelType.CHAT,
                128000,
                null,
                BigDecimal.valueOf(0.4),
                BigDecimal.valueOf(1.6),
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of(
                        "providerKind", "openai-compatible",
                        "temperature", 0.2
                )),
                ModelConnectivityStatus.PASSED,
                Instant.parse("2026-05-25T07:40:00Z"),
                "连接成功",
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T07:40:00Z")
        );
    }

    private static ModelConfig chatConfigWithOptions(Map<String, Object> options) {
        return new ModelConfig(
                TenantId.of("2001"),
                "4012",
                "3001",
                "gpt-4.1-mini",
                "GPT-4.1 mini option guard",
                ModelType.CHAT,
                128000,
                null,
                BigDecimal.valueOf(0.4),
                BigDecimal.valueOf(1.6),
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(options),
                ModelConnectivityStatus.PASSED,
                Instant.parse("2026-05-25T07:40:00Z"),
                "连接成功",
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T07:40:00Z")
        );
    }

    private static ModelConfig noAuthEmbeddingConfig() {
        return new ModelConfig(
                TenantId.of("2001"),
                "4002",
                "3002",
                "nomic-embed-text",
                "Nomic Embed Text",
                ModelType.EMBEDDING,
                8192,
                768,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of(
                        "providerKind", "ollama",
                        "dimensions", 768
                )),
                ModelConnectivityStatus.PASSED,
                Instant.parse("2026-05-25T07:30:00Z"),
                "连接成功",
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T08:00:00Z")
        );
    }

    private static ModelConfig embeddingConfigWithoutProviderKind() {
        return new ModelConfig(
                TenantId.of("2001"),
                "4004",
                "3001",
                "BAAI/bge-m3",
                "BGE M3",
                ModelType.EMBEDDING,
                8192,
                1536,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of("dimensions", 1536)),
                ModelConnectivityStatus.PASSED,
                Instant.parse("2026-05-25T07:30:00Z"),
                "连接成功",
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T08:00:00Z")
        );
    }

    private static ModelConfig embeddingConfigWithPlaintextSecret() {
        return new ModelConfig(
                TenantId.of("2001"),
                "4011",
                "3001",
                "unsafe-secret-options",
                "Unsafe secret options",
                ModelType.EMBEDDING,
                8192,
                1536,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of(
                        "providerKind", "openai-compatible",
                        "openAiApiKey", "sk-live-must-not-enter-outbox"
                )),
                ModelConnectivityStatus.PASSED,
                Instant.parse("2026-05-25T07:30:00Z"),
                "连接成功",
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T08:00:00Z")
        );
    }
}

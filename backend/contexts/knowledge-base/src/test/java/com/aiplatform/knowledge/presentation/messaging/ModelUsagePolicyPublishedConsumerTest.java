package com.aiplatform.knowledge.presentation.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.knowledge.domain.modelpolicy.KnowledgeModelUsagePolicyProjection;
import com.aiplatform.knowledge.domain.port.modelpolicy.KnowledgeModelUsagePolicyEventReceiptRepository;
import com.aiplatform.knowledge.domain.port.modelpolicy.KnowledgeModelUsagePolicyProjectionRepository;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.knowledge.infrastructure.messaging.converter.ModelUsagePolicyPublishedMessageConverter;
import com.aiplatform.knowledge.infrastructure.messaging.integrity.ModelUsagePolicyPayloadIntegrityVerifier;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.KbModelUsagePolicyPayloadDigestMapper;
import com.aiplatform.knowledge.presentation.messaging.consumer.ModelUsagePolicyPublishedConsumer;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

class ModelUsagePolicyPublishedConsumerTest {
    @Test
    void receiptAndProjectionAreDeclaredInsideOneTransactionBoundary() {
        assertThat(ModelUsagePolicyPublishedConsumer.class.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void convertsPublishedPolicyEventToKnowledgeProjection() {
        KnowledgeModelUsagePolicyProjectionRepository repository = mock(KnowledgeModelUsagePolicyProjectionRepository.class);
        KnowledgeModelUsagePolicyEventReceiptRepository receipts = acceptingReceipts();
        when(repository.save(any(KnowledgeModelUsagePolicyProjection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ModelUsagePolicyPublishedConsumer consumer = new ModelUsagePolicyPublishedConsumer(
                repository,
                receipts,
                new ModelUsagePolicyPublishedMessageConverter(new JsonMapper()),
                acceptingIntegrityVerifier()
        );

        consumer.onMessage(message());

        ArgumentCaptor<KnowledgeModelUsagePolicyProjection> captor = ArgumentCaptor.forClass(
                KnowledgeModelUsagePolicyProjection.class
        );
        verify(repository).save(captor.capture());
        KnowledgeModelUsagePolicyProjection projection = captor.getValue();
        assertThat(projection.tenantId().value()).isEqualTo("2001");
        assertThat(projection.modelConfigId()).isEqualTo("4001");
        assertThat(projection.providerCode()).isEqualTo("silicon-flow");
        assertThat(projection.connectionRef().endpoint()).isEqualTo("https://api.siliconflow.cn/v1");
        assertThat(projection.connectionRef().secretRefId()).isEqualTo("9001");
        assertThat(projection.capabilities()).containsExactly(KnowledgeModelCapability.EMBEDDING);
        assertThat(projection.allowedUseCases())
                .containsExactlyInAnyOrder(KnowledgeModelUseCase.RAG_INDEXING, KnowledgeModelUseCase.RAG_QUERY);
        assertThat(projection.enabled()).isTrue();
    }

    @Test
    void convertsNoAuthPublishedPolicyWithoutSecretReference() {
        KnowledgeModelUsagePolicyProjectionRepository repository = mock(KnowledgeModelUsagePolicyProjectionRepository.class);
        KnowledgeModelUsagePolicyEventReceiptRepository receipts = acceptingReceipts();
        when(repository.save(any(KnowledgeModelUsagePolicyProjection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ModelUsagePolicyPublishedConsumer consumer = new ModelUsagePolicyPublishedConsumer(
                repository,
                receipts,
                new ModelUsagePolicyPublishedMessageConverter(new JsonMapper()),
                acceptingIntegrityVerifier()
        );

        consumer.onMessage(noAuthMessage());

        ArgumentCaptor<KnowledgeModelUsagePolicyProjection> captor = ArgumentCaptor.forClass(
                KnowledgeModelUsagePolicyProjection.class
        );
        verify(repository).save(captor.capture());
        KnowledgeModelUsagePolicyProjection projection = captor.getValue();
        assertThat(projection.providerKind()).isEqualTo("ollama");
        assertThat(projection.connectionRef().authType()).isEqualTo("no_auth");
        assertThat(projection.connectionRef().secretRefId()).isNull();
        assertThat(projection.enabled()).isTrue();
    }

    @Test
    void duplicateReceiptSkipsProjectionMutation() {
        KnowledgeModelUsagePolicyProjectionRepository repository = mock(KnowledgeModelUsagePolicyProjectionRepository.class);
        KnowledgeModelUsagePolicyEventReceiptRepository receipts = mock(
                KnowledgeModelUsagePolicyEventReceiptRepository.class
        );
        when(receipts.recordIfAbsent(any(), anyLong(), any())).thenReturn(false);
        ModelUsagePolicyPublishedConsumer consumer = new ModelUsagePolicyPublishedConsumer(
                repository,
                receipts,
                new ModelUsagePolicyPublishedMessageConverter(new JsonMapper()),
                acceptingIntegrityVerifier()
        );

        consumer.onMessage(message());

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsUntrustedProducerBeforeRecordingReceiptOrProjection() {
        KnowledgeModelUsagePolicyProjectionRepository repository = mock(KnowledgeModelUsagePolicyProjectionRepository.class);
        KnowledgeModelUsagePolicyEventReceiptRepository receipts = mock(
                KnowledgeModelUsagePolicyEventReceiptRepository.class
        );
        ModelUsagePolicyPublishedConsumer consumer = new ModelUsagePolicyPublishedConsumer(
                repository,
                receipts,
                new ModelUsagePolicyPublishedMessageConverter(new JsonMapper()),
                acceptingIntegrityVerifier()
        );
        DomainEventMessage trusted = message();
        DomainEventMessage forged = new DomainEventMessage(
                trusted.messageId(),
                trusted.eventType(),
                trusted.tenantId(),
                trusted.aggregateType(),
                trusted.aggregateId(),
                trusted.version(),
                trusted.schemaVersion(),
                trusted.occurredAt(),
                trusted.traceId(),
                "forged-producer",
                trusted.payloadJson()
        );

        assertThatThrownBy(() -> consumer.onMessage(forged))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("生产者不受信任");
        verify(receipts, never()).recordIfAbsent(any(), anyLong(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsObsoleteV1WithoutFencingTheFollowingValidV2Event() {
        KnowledgeModelUsagePolicyProjectionRepository repository = mock(KnowledgeModelUsagePolicyProjectionRepository.class);
        KnowledgeModelUsagePolicyEventReceiptRepository receipts = acceptingReceipts();
        ModelUsagePolicyPublishedConsumer consumer = new ModelUsagePolicyPublishedConsumer(
                repository,
                receipts,
                new ModelUsagePolicyPublishedMessageConverter(new JsonMapper()),
                acceptingIntegrityVerifier()
        );
        DomainEventMessage current = message();
        DomainEventMessage obsolete = new DomainEventMessage(
                current.messageId(),
                current.eventType(),
                current.tenantId(),
                current.aggregateType(),
                current.aggregateId(),
                1,
                "v1",
                current.occurredAt(),
                current.traceId(),
                current.producer(),
                current.payloadJson()
        );

        assertThatThrownBy(() -> consumer.onMessage(obsolete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("协议");
        verify(receipts, never()).recordIfAbsent(any(), anyLong(), any());
        verify(repository, never()).save(any());

        consumer.onMessage(current);

        verify(receipts).recordIfAbsent("event-001", 2, Instant.parse("2026-05-25T08:00:00Z"));
        verify(repository).save(any(KnowledgeModelUsagePolicyProjection.class));
    }

    @Test
    void rejectsWrongReportedDigestBeforeRecordingReceiptOrProjection() {
        assertIntegrityFailureBeforePersistence(message(), "b".repeat(64));
    }

    @Test
    void rejectsFieldTamperingBeforeRecordingReceiptOrProjection() {
        DomainEventMessage original = message();
        assertIntegrityFailureBeforePersistence(
                withPayload(original, original.payloadJson().replace("BAAI/bge-m3", "forged-model")),
                "b".repeat(64)
        );
    }

    @Test
    void rejectsForgedMaximumRevisionBeforeItCanFenceLegitimatePolicies() {
        DomainEventMessage original = message();
        assertIntegrityFailureBeforePersistence(
                withPayload(original, original.payloadJson().replace(
                        "\"policyRevision\": 1",
                        "\"policyRevision\": 9223372036854775807"
                )),
                "a".repeat(64)
        );
    }

    private static void assertIntegrityFailureBeforePersistence(
            DomainEventMessage candidate,
            String canonicalDigest
    ) {
        KnowledgeModelUsagePolicyProjectionRepository repository = mock(KnowledgeModelUsagePolicyProjectionRepository.class);
        KnowledgeModelUsagePolicyEventReceiptRepository receipts = mock(
                KnowledgeModelUsagePolicyEventReceiptRepository.class
        );
        KbModelUsagePolicyPayloadDigestMapper mapper = mock(KbModelUsagePolicyPayloadDigestMapper.class);
        when(mapper.canonicalDigest(candidate.payloadJson())).thenReturn(canonicalDigest);
        ModelUsagePolicyPublishedConsumer consumer = new ModelUsagePolicyPublishedConsumer(
                repository,
                receipts,
                new ModelUsagePolicyPublishedMessageConverter(new JsonMapper()),
                new ModelUsagePolicyPayloadIntegrityVerifier(mapper, new JsonMapper())
        );

        assertThatThrownBy(() -> consumer.onMessage(candidate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("完整性");

        verify(receipts, never()).recordIfAbsent(any(), anyLong(), any());
        verify(repository, never()).save(any());
    }

    private static ModelUsagePolicyPayloadIntegrityVerifier acceptingIntegrityVerifier() {
        return mock(ModelUsagePolicyPayloadIntegrityVerifier.class);
    }

    private static KnowledgeModelUsagePolicyEventReceiptRepository acceptingReceipts() {
        KnowledgeModelUsagePolicyEventReceiptRepository receipts = mock(
                KnowledgeModelUsagePolicyEventReceiptRepository.class
        );
        when(receipts.recordIfAbsent(any(), anyLong(), any())).thenReturn(true);
        return receipts;
    }

    private static DomainEventMessage message() {
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
                """
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
                          "effectiveOptionsDigest": "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                          "maxInputTokens": 8192,
                          "maxOutputTokens": 0,
                          "policyVersion": "v1",
                          "policyRevision": 1,
                          "policyDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                          "enabled": true
                        }
                        """
        );
    }

    private static DomainEventMessage noAuthMessage() {
        return new DomainEventMessage(
                "event-002",
                "ModelUsagePolicyPublished",
                "2001",
                "ModelConfig",
                "4002",
                2,
                "schema/model-usage-policy.v2",
                Instant.parse("2026-05-25T08:00:00Z"),
                "trace-002",
                "model-hub",
                """
                        {
                          "modelConfigId": "4002",
                          "providerId": "3002",
                          "providerCode": "local-ollama",
                          "providerKind": "ollama",
                          "providerEndpoint": "http://localhost:11434",
                          "authType": "no_auth",
                          "secretRefId": null,
                          "secretMaskedValue": null,
                          "modelCode": "nomic-embed-text",
                          "modelConfigVersion": 8,
                          "modelType": "EMBEDDING",
                          "capabilities": ["EMBEDDING"],
                          "allowedUseCases": ["RAG_INDEXING", "RAG_QUERY"],
                          "allowedKnowledgeBaseIds": [],
                          "effectiveOptions": {"dimensions": 768},
                          "effectiveOptionsDigest": "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                          "maxInputTokens": 8192,
                          "maxOutputTokens": 0,
                          "policyVersion": "v2",
                          "policyRevision": 2,
                          "policyDigest": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                          "enabled": true
                        }
                        """
        );
    }

    private static DomainEventMessage withPayload(DomainEventMessage message, String payloadJson) {
        return new DomainEventMessage(
                message.messageId(),
                message.eventType(),
                message.tenantId(),
                message.aggregateType(),
                message.aggregateId(),
                message.version(),
                message.schemaVersion(),
                message.occurredAt(),
                message.traceId(),
                message.producer(),
                payloadJson
        );
    }
}

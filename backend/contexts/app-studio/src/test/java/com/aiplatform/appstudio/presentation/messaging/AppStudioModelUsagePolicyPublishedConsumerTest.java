package com.aiplatform.appstudio.presentation.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.domain.modelpolicy.AppModelUsagePolicyProjection;
import com.aiplatform.appstudio.domain.port.modelpolicy.AppModelUsagePolicyProjectionRepository;
import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyEventReceiptRepository;
import com.aiplatform.appstudio.infrastructure.messaging.converter.ModelUsagePolicyPublishedMessageConverter;
import com.aiplatform.appstudio.infrastructure.messaging.integrity.ModelUsagePolicyPayloadIntegrityVerifier;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppModelUsagePolicyPayloadDigestMapper;
import com.aiplatform.appstudio.presentation.messaging.consumer.AppStudioModelUsagePolicyPublishedConsumer;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppStudioModelUsagePolicyPublishedConsumerTest {
    @Test
    void convertsPublishedPolicyEventToAppProjection() {
        AppModelUsagePolicyProjectionRepository repository = mock(AppModelUsagePolicyProjectionRepository.class);
        ModelUsagePolicyEventReceiptRepository receipts = acceptingReceipts();
        when(repository.save(any(AppModelUsagePolicyProjection.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AppStudioModelUsagePolicyPublishedConsumer consumer = new AppStudioModelUsagePolicyPublishedConsumer(
                repository,
                receipts,
                new ModelUsagePolicyPublishedMessageConverter(new JsonMapper()),
                acceptingIntegrityVerifier()
        );

        consumer.onMessage(message());

        ArgumentCaptor<AppModelUsagePolicyProjection> captor = ArgumentCaptor.forClass(AppModelUsagePolicyProjection.class);
        verify(repository).save(captor.capture());
        AppModelUsagePolicyProjection projection = captor.getValue();
        assertThat(projection.tenantId().value()).isEqualTo("2001");
        assertThat(projection.modelConfigId()).isEqualTo("4001");
        assertThat(projection.providerCode()).isEqualTo("silicon-flow");
        assertThat(projection.connectionRef().endpoint()).isEqualTo("https://api.siliconflow.cn/v1");
        assertThat(projection.connectionRef().secretRefId()).isEqualTo("9001");
        assertThat(projection.capabilities()).containsExactly("CHAT");
        assertThat(projection.allowedUseCases()).containsExactly("RAG_QUERY");
        assertThat(projection.allowedApplicationIds()).containsExactly("7001");
        assertThat(projection.enabled()).isTrue();
    }

    @Test
    void duplicateEventIdIsPersistentlyClaimedOnlyOnceBeforeProjectionMutation() {
        AppModelUsagePolicyProjectionRepository repository = mock(AppModelUsagePolicyProjectionRepository.class);
        ModelUsagePolicyEventReceiptRepository receipts = mock(ModelUsagePolicyEventReceiptRepository.class);
        when(receipts.recordIfAbsent("event-001", 2, Instant.parse("2026-05-25T08:00:00Z")))
                .thenReturn(true, false);
        when(repository.save(any(AppModelUsagePolicyProjection.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AppStudioModelUsagePolicyPublishedConsumer consumer = new AppStudioModelUsagePolicyPublishedConsumer(
                repository,
                receipts,
                new ModelUsagePolicyPublishedMessageConverter(new JsonMapper()),
                acceptingIntegrityVerifier()
        );

        consumer.onMessage(message());
        consumer.onMessage(message());

        verify(receipts, times(2)).recordIfAbsent(
                "event-001",
                2,
                Instant.parse("2026-05-25T08:00:00Z")
        );
        verify(repository, times(1)).save(any(AppModelUsagePolicyProjection.class));
    }

    @Test
    void rejectsObsoleteV1WithoutFencingTheFollowingValidV2Event() {
        AppModelUsagePolicyProjectionRepository repository = mock(AppModelUsagePolicyProjectionRepository.class);
        ModelUsagePolicyEventReceiptRepository receipts = acceptingReceipts();
        AppStudioModelUsagePolicyPublishedConsumer consumer = new AppStudioModelUsagePolicyPublishedConsumer(
                repository,
                receipts,
                new ModelUsagePolicyPublishedMessageConverter(new JsonMapper()),
                acceptingIntegrityVerifier()
        );

        assertThatThrownBy(() -> consumer.onMessage(message(1, "v1", "4001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("协议");

        verify(repository, org.mockito.Mockito.never()).save(any(AppModelUsagePolicyProjection.class));
        verify(receipts, org.mockito.Mockito.never()).recordIfAbsent(any(), any(Long.class), any(Instant.class));

        consumer.onMessage(message());

        verify(receipts).recordIfAbsent("event-001", 2, Instant.parse("2026-05-25T08:00:00Z"));
        verify(repository).save(any(AppModelUsagePolicyProjection.class));
    }

    @Test
    void rejectsPayloadWhoseModelIdentityDoesNotMatchTheEnvelope() {
        AppModelUsagePolicyProjectionRepository repository = mock(AppModelUsagePolicyProjectionRepository.class);
        ModelUsagePolicyEventReceiptRepository receipts = acceptingReceipts();
        AppStudioModelUsagePolicyPublishedConsumer consumer = new AppStudioModelUsagePolicyPublishedConsumer(
                repository,
                receipts,
                new ModelUsagePolicyPublishedMessageConverter(new JsonMapper()),
                acceptingIntegrityVerifier()
        );

        assertThatThrownBy(() -> consumer.onMessage(message(2, "schema/model-usage-policy.v2", "4999")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("聚合 ID");

        verify(repository, org.mockito.Mockito.never()).save(any(AppModelUsagePolicyProjection.class));
    }

    @Test
    void rejectsWrongReportedDigestBeforeRecordingReceiptOrProjection() {
        assertIntegrityFailureBeforePersistence(message(), "b".repeat(64));
    }

    @Test
    void rejectsFieldTamperingBeforeRecordingReceiptOrProjection() {
        DomainEventMessage original = message();
        assertIntegrityFailureBeforePersistence(
                withPayload(original, original.payloadJson().replace("deepseek-chat", "forged-model")),
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
        AppModelUsagePolicyProjectionRepository repository = mock(AppModelUsagePolicyProjectionRepository.class);
        ModelUsagePolicyEventReceiptRepository receipts = mock(ModelUsagePolicyEventReceiptRepository.class);
        AppModelUsagePolicyPayloadDigestMapper mapper = mock(AppModelUsagePolicyPayloadDigestMapper.class);
        when(mapper.canonicalDigest(candidate.payloadJson())).thenReturn(canonicalDigest);
        AppStudioModelUsagePolicyPublishedConsumer consumer = new AppStudioModelUsagePolicyPublishedConsumer(
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

    private static ModelUsagePolicyEventReceiptRepository acceptingReceipts() {
        ModelUsagePolicyEventReceiptRepository receipts = mock(ModelUsagePolicyEventReceiptRepository.class);
        when(receipts.recordIfAbsent(any(), any(Long.class), any(Instant.class))).thenReturn(true);
        return receipts;
    }

    private static DomainEventMessage message() {
        return message(2, "schema/model-usage-policy.v2", "4001");
    }

    private static DomainEventMessage message(long version, String schemaVersion, String aggregateId) {
        return new DomainEventMessage(
                "event-001",
                "ModelUsagePolicyPublished",
                "2001",
                "ModelConfig",
                aggregateId,
                version,
                schemaVersion,
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
                          "modelCode": "deepseek-chat",
                          "modelConfigVersion": 7,
                          "modelType": "CHAT",
                          "capabilities": ["CHAT"],
                          "allowedUseCases": ["RAG_QUERY"],
                          "allowedApplicationIds": ["7001"],
                          "effectiveOptions": {"temperature": 0.2},
                          "effectiveOptionsDigest": "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                          "maxInputTokens": 8192,
                          "maxOutputTokens": 2048,
                          "policyVersion": "v1",
                          "policyRevision": 1,
                          "policyDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
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

package com.aiplatform.infra.rocketmq.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

class DomainEventMessageTest {
    private static final Instant OCCURRED_AT = Instant.parse("2026-05-22T08:00:00Z");

    @Test
    void rejectsBlankEnvelopeFactsInsteadOfPublishingInvalidDomainEvent() {
        assertBlankRejected("消息 ID", " ", "DocumentIngestRequested", "2001", "KnowledgeDocument", "8001", "v1",
                "TRACE-KB-DOC-001", "knowledge-base", "{\"documentId\":\"8001\"}");
        assertBlankRejected("事件类型", "msg-1", " ", "2001", "KnowledgeDocument", "8001", "v1",
                "TRACE-KB-DOC-001", "knowledge-base", "{\"documentId\":\"8001\"}");
        assertBlankRejected("租户 ID", "msg-1", "DocumentIngestRequested", " ", "KnowledgeDocument", "8001", "v1",
                "TRACE-KB-DOC-001", "knowledge-base", "{\"documentId\":\"8001\"}");
        assertBlankRejected("聚合类型", "msg-1", "DocumentIngestRequested", "2001", " ", "8001", "v1",
                "TRACE-KB-DOC-001", "knowledge-base", "{\"documentId\":\"8001\"}");
        assertBlankRejected("聚合 ID", "msg-1", "DocumentIngestRequested", "2001", "KnowledgeDocument", " ", "v1",
                "TRACE-KB-DOC-001", "knowledge-base", "{\"documentId\":\"8001\"}");
        assertBlankRejected("事件协议版本", "msg-1", "DocumentIngestRequested", "2001", "KnowledgeDocument", "8001",
                " ", "TRACE-KB-DOC-001", "knowledge-base", "{\"documentId\":\"8001\"}");
        assertBlankRejected("链路 ID", "msg-1", "DocumentIngestRequested", "2001", "KnowledgeDocument", "8001",
                "v1", " ", "knowledge-base", "{\"documentId\":\"8001\"}");
        assertBlankRejected("生产者", "msg-1", "DocumentIngestRequested", "2001", "KnowledgeDocument", "8001", "v1",
                "TRACE-KB-DOC-001", " ", "{\"documentId\":\"8001\"}");
        assertBlankRejected("事件载荷", "msg-1", "DocumentIngestRequested", "2001", "KnowledgeDocument", "8001", "v1",
                "TRACE-KB-DOC-001", "knowledge-base", " ");
    }

    @Test
    void rejectsNonPositiveEventVersion() {
        assertThatThrownBy(() -> eventMessage(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("事件版本必须大于 0");
    }

    @Test
    void rejectsMissingOccurredAtInsteadOfJdkNullPointerException() {
        assertThatThrownBy(() -> new DomainEventMessage(
                "msg-1",
                "DocumentIngestRequested",
                "2001",
                "KnowledgeDocument",
                "8001",
                1,
                "v1",
                null,
                "TRACE-KB-DOC-001",
                "knowledge-base",
                "{\"documentId\":\"8001\"}"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("事件发生时间不能为空");
    }

    @Test
    void trimsEnvelopeFactsBeforePublishing() {
        DomainEventMessage message = new DomainEventMessage(
                " msg-1 ",
                " DocumentIngestRequested ",
                " 2001 ",
                " KnowledgeDocument ",
                " 8001 ",
                1,
                " v1 ",
                OCCURRED_AT,
                " TRACE-KB-DOC-001 ",
                " knowledge-base ",
                " {\"documentId\":\"8001\"} "
        );

        assertThat(message.messageId()).isEqualTo("msg-1");
        assertThat(message.eventType()).isEqualTo("DocumentIngestRequested");
        assertThat(message.tenantId()).isEqualTo("2001");
        assertThat(message.aggregateType()).isEqualTo("KnowledgeDocument");
        assertThat(message.aggregateId()).isEqualTo("8001");
        assertThat(message.schemaVersion()).isEqualTo("v1");
        assertThat(message.traceId()).isEqualTo("TRACE-KB-DOC-001");
        assertThat(message.producer()).isEqualTo("knowledge-base");
        assertThat(message.payloadJson()).isEqualTo("{\"documentId\":\"8001\"}");
    }

    @Test
    void serializesOccurredAtAsAnExactIsoInstantInsteadOfLosingSubMillisecondEvidence() throws Exception {
        Instant microsecondOccurredAt = Instant.parse("2026-07-21T13:51:03.377581Z");
        DomainEventMessage message = new DomainEventMessage(
                "msg-microsecond-1",
                "ModelConfigApprovalRequested",
                "2001",
                "ModelProvider",
                "1",
                1,
                "v2",
                microsecondOccurredAt,
                "TRACE-MODEL-APPROVAL-001",
                "model-hub",
                "{\"approvalRequestId\":\"REQ-1001\"}"
        );

        JsonMapper brokerMapper = JsonMapper.builder()
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DateTimeFeature.TRUNCATE_TO_MSECS_ON_WRITE)
                .build();
        String json = brokerMapper.writeValueAsString(message);
        DomainEventMessage restored = brokerMapper.readValue(json, DomainEventMessage.class);

        assertThat(json).contains("\"occurredAt\":\"2026-07-21T13:51:03.377581Z\"");
        assertThat(restored).isEqualTo(message);
    }

    private static void assertBlankRejected(
            String fieldName,
            String messageId,
            String eventType,
            String tenantId,
            String aggregateType,
            String aggregateId,
            String schemaVersion,
            String traceId,
            String producer,
            String payloadJson
    ) {
        assertThatThrownBy(() -> eventMessage(
                messageId,
                eventType,
                tenantId,
                aggregateType,
                aggregateId,
                1,
                schemaVersion,
                traceId,
                producer,
                payloadJson
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(fieldName + "不能为空");
    }

    private static DomainEventMessage eventMessage(long version) {
        return eventMessage(
                "msg-1",
                "DocumentIngestRequested",
                "2001",
                "KnowledgeDocument",
                "8001",
                version,
                "v1",
                "TRACE-KB-DOC-001",
                "knowledge-base",
                "{\"documentId\":\"8001\"}"
        );
    }

    private static DomainEventMessage eventMessage(
            String messageId,
            String eventType,
            String tenantId,
            String aggregateType,
            String aggregateId,
            long version,
            String schemaVersion,
            String traceId,
            String producer,
            String payloadJson
    ) {
        return new DomainEventMessage(
                messageId,
                eventType,
                tenantId,
                aggregateType,
                aggregateId,
                version,
                schemaVersion,
                OCCURRED_AT,
                traceId,
                producer,
                payloadJson
        );
    }
}

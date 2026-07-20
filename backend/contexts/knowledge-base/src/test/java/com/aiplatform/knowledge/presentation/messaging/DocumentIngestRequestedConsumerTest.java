package com.aiplatform.knowledge.presentation.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.knowledge.application.job.KnowledgeDocumentJobWorker;
import com.aiplatform.knowledge.infrastructure.messaging.converter.DocumentIngestRequestedMessageConverter;
import com.aiplatform.knowledge.presentation.messaging.consumer.DocumentIngestRequestedConsumer;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DocumentIngestRequestedConsumerTest {
    @Test
    void consumesDocumentIngestRequestedEventByIngestJobId() {
        KnowledgeDocumentJobWorker worker = mock(KnowledgeDocumentJobWorker.class);
        DocumentIngestRequestedConsumer consumer = consumer(worker);

        consumer.onMessage(message("DocumentIngestRequested", "DOCUMENT_PARSE", "PENDING"));

        verify(worker).drainAvailable();
    }

    @Test
    void normalizesPayloadFactsBeforeBuildingParseCommand() {
        KnowledgeDocumentJobWorker worker = mock(KnowledgeDocumentJobWorker.class);
        DocumentIngestRequestedConsumer consumer = consumer(worker);

        consumer.onMessage(message(
                "DocumentIngestRequested",
                "KnowledgeDocument",
                "8001",
                " DOCUMENT_PARSE ",
                " PENDING ",
                " 8101 ",
                " 7001 ",
                " 8001 "
        ));

        verify(worker).drainAvailable();
    }

    @Test
    void ignoresOtherDomainEventsOnSharedTopic() {
        KnowledgeDocumentJobWorker worker = mock(KnowledgeDocumentJobWorker.class);
        DocumentIngestRequestedConsumer consumer = consumer(worker);

        consumer.onMessage(message("ModelUsagePolicyPublished", "DOCUMENT_PARSE", "PENDING"));

        verifyNoInteractions(worker);
    }

    @Test
    void rejectsUnsupportedIngestJobPayload() {
        KnowledgeDocumentJobWorker worker = mock(KnowledgeDocumentJobWorker.class);
        DocumentIngestRequestedConsumer consumer = consumer(worker);

        assertThatThrownBy(() -> consumer.onMessage(message("DocumentIngestRequested", "DOCUMENT_INDEX", "PENDING")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档入库事件任务类型不支持");
        verifyNoInteractions(worker);
    }

    @Test
    void rejectsMismatchedAggregateEnvelope() {
        KnowledgeDocumentJobWorker worker = mock(KnowledgeDocumentJobWorker.class);
        DocumentIngestRequestedConsumer consumer = consumer(worker);

        assertThatThrownBy(() -> consumer.onMessage(message(
                "DocumentIngestRequested",
                "KnowledgeDocument",
                "8002",
                "DOCUMENT_PARSE",
                "PENDING"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档入库事件聚合 ID 与载荷文档 ID 不一致");
        verifyNoInteractions(worker);
    }

    private static DocumentIngestRequestedConsumer consumer(KnowledgeDocumentJobWorker worker) {
        return new DocumentIngestRequestedConsumer(
                worker,
                new DocumentIngestRequestedMessageConverter(new JsonMapper())
        );
    }

    private static DomainEventMessage message(String eventType, String jobType, String status) {
        return message(eventType, "KnowledgeDocument", "8001", jobType, status);
    }

    private static DomainEventMessage message(
            String eventType,
            String aggregateType,
            String aggregateId,
            String jobType,
            String status
    ) {
        return message(eventType, aggregateType, aggregateId, jobType, status, "8101", "7001", "8001");
    }

    private static DomainEventMessage message(
            String eventType,
            String aggregateType,
            String aggregateId,
            String jobType,
            String status,
            String ingestJobId,
            String knowledgeBaseId,
            String documentId
    ) {
        return new DomainEventMessage(
                "event-001",
                eventType,
                "2001",
                aggregateType,
                aggregateId,
                1,
                "v1",
                Instant.parse("2026-05-22T08:00:00Z"),
                "TRACE-KB-DOC-001",
                "knowledge-base",
                """
                        {
                          "ingestJobId": "%s",
                          "knowledgeBaseId": "%s",
                          "documentId": "%s",
                          "jobType": "%s",
                          "status": "%s"
                        }
                        """.formatted(ingestJobId, knowledgeBaseId, documentId, jobType, status)
        );
    }
}

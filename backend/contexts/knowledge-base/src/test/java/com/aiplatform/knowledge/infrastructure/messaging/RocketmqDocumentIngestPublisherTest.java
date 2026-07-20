package com.aiplatform.knowledge.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.infra.rocketmq.publisher.DomainEventPublisher;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RocketmqDocumentIngestPublisherTest {
    @Test
    void mapsIngestJobToDocumentIngestRequestedDomainEvent() throws Exception {
        DomainEventPublisher domainEvents = mock(DomainEventPublisher.class);
        RocketmqDocumentIngestPublisher publisher = new RocketmqDocumentIngestPublisher(domainEvents, new JsonMapper());

        publisher.publish(new DocumentIngestJob(
                TenantId.of("2001"),
                "8101",
                "8001",
                "7001",
                "DOCUMENT_PARSE",
                "PENDING",
                "TRACE-KB-DOC-001",
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z"),
                "1001",
                ""
        ));

        ArgumentCaptor<DomainEventMessage> messageCaptor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(domainEvents).publish(messageCaptor.capture());
        DomainEventMessage message = messageCaptor.getValue();
        assertThat(message.messageId()).isEqualTo("DocumentIngestRequested:2001:8001:8101");
        assertThat(message.eventType()).isEqualTo("DocumentIngestRequested");
        assertThat(message.tenantId()).isEqualTo("2001");
        assertThat(message.aggregateType()).isEqualTo("KnowledgeDocument");
        assertThat(message.aggregateId()).isEqualTo("8001");
        assertThat(message.version()).isEqualTo(1);
        assertThat(message.occurredAt()).isEqualTo(Instant.parse("2026-05-22T08:00:00Z"));
        assertThat(message.traceId()).isEqualTo("TRACE-KB-DOC-001");
        assertThat(message.producer()).isEqualTo("knowledge-base");
        JsonNode payload = new JsonMapper().readTree(message.payloadJson());
        assertThat(payload.get("ingestJobId").asText()).isEqualTo("8101");
        assertThat(payload.get("knowledgeBaseId").asText()).isEqualTo("7001");
        assertThat(payload.get("documentId").asText()).isEqualTo("8001");
        assertThat(payload.get("jobType").asText()).isEqualTo("DOCUMENT_PARSE");
        assertThat(payload.get("status").asText()).isEqualTo("PENDING");
    }
}

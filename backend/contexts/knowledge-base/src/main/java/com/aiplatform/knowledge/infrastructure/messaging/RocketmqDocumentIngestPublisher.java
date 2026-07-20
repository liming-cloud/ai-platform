package com.aiplatform.knowledge.infrastructure.messaging;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.infra.rocketmq.publisher.DomainEventPublisher;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.port.DocumentIngestPublisher;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.cloud.stream.bindings.domainEvent-out-0.destination")
public class RocketmqDocumentIngestPublisher implements DocumentIngestPublisher {
    private static final String EVENT_TYPE = "DocumentIngestRequested";
    private static final String AGGREGATE_TYPE = "KnowledgeDocument";
    private static final String PRODUCER = "knowledge-base";

    private final DomainEventPublisher domainEventPublisher;
    private final JsonMapper objectMapper;

    public RocketmqDocumentIngestPublisher(DomainEventPublisher domainEventPublisher, JsonMapper objectMapper) {
        this.domainEventPublisher = Objects.requireNonNull(domainEventPublisher, "领域事件发布器不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    @Override
    public void publish(DocumentIngestJob job) {
        Objects.requireNonNull(job, "文档导入任务不能为空");
        domainEventPublisher.publish(new DomainEventMessage(
                "DocumentIngestRequested:%s:%s:%s".formatted(
                        job.tenantId().value(),
                        job.documentId(),
                        job.jobId()
                ),
                EVENT_TYPE,
                job.tenantId().value(),
                AGGREGATE_TYPE,
                job.documentId(),
                1,
                "v1",
                job.submittedAt(),
                job.traceId(),
                PRODUCER,
                payloadJson(job)
        ));
    }

    private String payloadJson(DocumentIngestJob job) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "ingestJobId", job.jobId(),
                    "knowledgeBaseId", job.knowledgeBaseId(),
                    "documentId", job.documentId(),
                    "jobType", job.jobType(),
                    "status", job.status().name()
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("文档导入事件载荷序列化失败", exception);
        }
    }
}

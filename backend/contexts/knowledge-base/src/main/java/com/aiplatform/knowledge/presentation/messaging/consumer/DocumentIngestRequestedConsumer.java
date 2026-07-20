package com.aiplatform.knowledge.presentation.messaging.consumer;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.knowledge.application.job.KnowledgeDocumentJobWorker;
import com.aiplatform.knowledge.infrastructure.messaging.converter.DocumentIngestRequestedMessageConverter;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 文档入库请求事件消费者。消息仅是 best-effort wake，数据库作业事实仍决定实际执行。
 */
@Component("documentIngestRequested")
@ConditionalOnProperty(name = "spring.cloud.stream.bindings.documentIngestRequested-in-0.destination")
public class DocumentIngestRequestedConsumer implements Consumer<DomainEventMessage> {
    private static final String EVENT_TYPE = "DocumentIngestRequested";

    private final KnowledgeDocumentJobWorker worker;
    private final DocumentIngestRequestedMessageConverter converter;

    public DocumentIngestRequestedConsumer(
            KnowledgeDocumentJobWorker worker,
            DocumentIngestRequestedMessageConverter converter
    ) {
        this.worker = Objects.requireNonNull(worker, "文档作业 worker 不能为空");
        this.converter = Objects.requireNonNull(converter, "文档入库事件转换器不能为空");
    }

    @Override
    public void accept(DomainEventMessage message) {
        onMessage(message);
    }

    public void onMessage(DomainEventMessage message) {
        Objects.requireNonNull(message, "领域事件不能为空");
        if (!EVENT_TYPE.equals(message.eventType())) {
            return;
        }
        converter.toCommand(message);
        worker.drainAvailable();
    }
}

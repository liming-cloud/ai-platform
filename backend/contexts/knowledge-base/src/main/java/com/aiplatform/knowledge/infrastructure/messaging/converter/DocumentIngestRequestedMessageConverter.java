package com.aiplatform.knowledge.infrastructure.messaging.converter;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.knowledge.application.command.ParseDocumentIngestJobCommand;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.infrastructure.messaging.model.DocumentIngestRequestedPayload;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 文档入库事件转换器，将消息协议收敛为应用层可执行的解析命令。
 */
@Component
public class DocumentIngestRequestedMessageConverter {
    private static final String AGGREGATE_TYPE = "KnowledgeDocument";
    private static final String PENDING_STATUS = "PENDING";

    private final JsonMapper objectMapper;

    public DocumentIngestRequestedMessageConverter(JsonMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    public ParseDocumentIngestJobCommand toCommand(DomainEventMessage message) {
        Objects.requireNonNull(message, "领域事件不能为空");
        DocumentIngestRequestedPayload payload = payload(message);
        NormalizedDocumentIngestRequestedPayload normalizedPayload = validatePayload(payload);
        validateEnvelope(message, normalizedPayload.documentId());
        return new ParseDocumentIngestJobCommand(
                TenantId.of(message.tenantId()),
                normalizedPayload.ingestJobId(),
                normalizedPayload.knowledgeBaseId(),
                normalizedPayload.documentId()
        );
    }

    private DocumentIngestRequestedPayload payload(DomainEventMessage message) {
        try {
            return objectMapper.readValue(message.payloadJson(), DocumentIngestRequestedPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("文档入库事件载荷解析失败", exception);
        }
    }

    private static NormalizedDocumentIngestRequestedPayload validatePayload(DocumentIngestRequestedPayload payload) {
        Objects.requireNonNull(payload, "文档入库事件载荷不能为空");
        String jobType = requireText(payload.jobType(), "任务类型");
        if (!DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE.equals(jobType)) {
            throw new IllegalArgumentException("文档入库事件任务类型不支持：" + jobType);
        }
        String status = requireText(payload.status(), "任务状态");
        if (!PENDING_STATUS.equals(status)) {
            throw new IllegalArgumentException("文档入库事件任务状态不支持：" + status);
        }
        return new NormalizedDocumentIngestRequestedPayload(
                payload.ingestJobId(),
                payload.knowledgeBaseId(),
                payload.documentId()
        );
    }

    private static void validateEnvelope(DomainEventMessage message, String documentId) {
        if (!AGGREGATE_TYPE.equals(message.aggregateType())) {
            throw new IllegalArgumentException("文档入库事件聚合类型不支持：" + message.aggregateType());
        }
        if (!documentId.equals(message.aggregateId())) {
            throw new IllegalArgumentException("文档入库事件聚合 ID 与载荷文档 ID 不一致");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

}

package com.aiplatform.knowledge.infrastructure.messaging.model;

/**
 * 文档入库请求事件载荷，承载异步解析消费者定位任务所需的最小字段。
 */
public record DocumentIngestRequestedPayload(
        String ingestJobId,
        String knowledgeBaseId,
        String documentId,
        String jobType,
        String status
) {
}

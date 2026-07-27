package com.aiplatform.foundation.controlplane.web.knowledge;

/**
 * 发布文本数据源的公共请求。
 */
public record PublishTextDataSourceRequest(
        String knowledgeSpaceId,
        Long expectedVersion) {
}

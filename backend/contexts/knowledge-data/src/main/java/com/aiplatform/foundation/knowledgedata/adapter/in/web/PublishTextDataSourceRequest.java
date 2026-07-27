package com.aiplatform.foundation.knowledgedata.adapter.in.web;

/**
 * 发布文本数据源的公共请求。
 */
public record PublishTextDataSourceRequest(
        String knowledgeSpaceId,
        Long expectedVersion) {
}

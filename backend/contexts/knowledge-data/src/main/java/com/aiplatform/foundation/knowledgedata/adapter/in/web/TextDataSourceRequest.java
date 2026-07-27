package com.aiplatform.foundation.knowledgedata.adapter.in.web;

/**
 * 创建文本数据源的公共请求。
 */
public record TextDataSourceRequest(
        String knowledgeSpaceId,
        String name) {
}

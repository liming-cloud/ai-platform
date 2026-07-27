package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.request;

/**
 * 创建文本数据源的公共请求。
 */
public record TextDataSourceRequest(
        String knowledgeSpaceId,
        String name) {
}

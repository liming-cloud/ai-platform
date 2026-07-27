package com.aiplatform.foundation.knowledgedata.adapter.in.web;

/**
 * 创建知识空间的公共请求。
 */
public record KnowledgeSpaceRequest(
        String name,
        String description) {
}

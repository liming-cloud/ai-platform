package com.aiplatform.foundation.controlplane.web.knowledge;

/**
 * 创建知识空间的公共请求。
 */
public record KnowledgeSpaceRequest(
        String name,
        String description) {
}

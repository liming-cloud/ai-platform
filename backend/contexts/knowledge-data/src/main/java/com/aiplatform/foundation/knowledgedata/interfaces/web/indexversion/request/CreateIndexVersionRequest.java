package com.aiplatform.foundation.knowledgedata.interfaces.web.indexversion.request;

/**
 * 创建索引版本时显式选择嵌入部署。
 */
public record CreateIndexVersionRequest(String embeddingDeploymentId) {
}

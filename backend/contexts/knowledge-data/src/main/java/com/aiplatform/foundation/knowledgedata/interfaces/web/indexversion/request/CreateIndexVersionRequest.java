package com.aiplatform.foundation.knowledgedata.interfaces.web.indexversion.request;

/**
 * 创建索引版本时显式选择可用的嵌入模型配置。
 */
public record CreateIndexVersionRequest(String embeddingModelConfigurationId) {
}

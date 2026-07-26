package com.aiplatform.foundation.knowledgedata.application.indexversion;

/**
 * 选择的嵌入部署不存在、未发布或类型不正确。
 */
public final class IndexEmbeddingDeploymentUnavailableException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IndexEmbeddingDeploymentUnavailableException() {
        super("索引只能使用当前租户已发布的嵌入部署");
    }
}

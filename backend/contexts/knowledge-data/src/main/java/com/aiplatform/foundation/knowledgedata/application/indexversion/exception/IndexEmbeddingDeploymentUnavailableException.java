package com.aiplatform.foundation.knowledgedata.application.indexversion.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 选择的嵌入部署不存在、未发布或类型不正确。
 */
public final class IndexEmbeddingDeploymentUnavailableException
        extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public IndexEmbeddingDeploymentUnavailableException() {
        super(KnowledgeDataErrorCode.EMBEDDING_DEPLOYMENT_UNAVAILABLE);
    }
}

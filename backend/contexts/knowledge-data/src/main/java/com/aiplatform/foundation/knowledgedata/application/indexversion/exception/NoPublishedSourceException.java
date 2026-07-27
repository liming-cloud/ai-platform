package com.aiplatform.foundation.knowledgedata.application.indexversion.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 知识空间当前没有可冻结的已发布内容。
 */
public final class NoPublishedSourceException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public NoPublishedSourceException() {
        super(KnowledgeDataErrorCode.NO_PUBLISHED_SOURCE);
    }
}

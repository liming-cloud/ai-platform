package com.aiplatform.foundation.knowledgedata.application.knowledge.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 当前租户已经存在同名知识空间。
 */
public final class KnowledgeSpaceNameConflictException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public KnowledgeSpaceNameConflictException() {
        super(KnowledgeDataErrorCode.KNOWLEDGE_SPACE_NAME_CONFLICT);
    }
}

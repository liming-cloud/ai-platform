package com.aiplatform.foundation.knowledgedata.application.knowledge.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 当前租户的知识空间数量已经达到批准上限。
 */
public final class KnowledgeSpaceQuotaExceededException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public KnowledgeSpaceQuotaExceededException() {
        super(KnowledgeDataErrorCode.KNOWLEDGE_SPACE_QUOTA_EXCEEDED);
    }
}

package com.aiplatform.foundation.knowledgedata.application.knowledge.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 同一幂等键被用于不同请求。
 */
public final class IdempotencyConflictException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public IdempotencyConflictException() {
        super(KnowledgeDataErrorCode.IDEMPOTENCY_CONFLICT);
    }
}

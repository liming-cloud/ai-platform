package com.aiplatform.foundation.trustedquery.application.run.exception;

import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.trustedquery.domain.common.error.TrustedQueryErrorCode;
/**
 * 相同幂等键被用于不同查询请求。
 */
public final class QueryRunIdempotencyConflictException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public QueryRunIdempotencyConflictException() {
        super(TrustedQueryErrorCode.IDEMPOTENCY);
    }
}

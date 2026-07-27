package com.aiplatform.foundation.trustedquery.application.run.exception;

import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.trustedquery.domain.common.error.TrustedQueryErrorCode;
/**
 * 相同幂等请求仍在处理中。
 */
public final class QueryRunInProgressException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public QueryRunInProgressException() {
        super(TrustedQueryErrorCode.IN_PROGRESS);
    }
}

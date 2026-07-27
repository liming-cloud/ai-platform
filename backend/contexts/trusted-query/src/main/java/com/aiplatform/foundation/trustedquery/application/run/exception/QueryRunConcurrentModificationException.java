package com.aiplatform.foundation.trustedquery.application.run.exception;

import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.trustedquery.domain.common.error.TrustedQueryErrorCode;
/**
 * 查询运行终态被其他执行者抢先提交。
 */
public final class QueryRunConcurrentModificationException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public QueryRunConcurrentModificationException() {
        super(TrustedQueryErrorCode.CONCURRENT);
    }
}

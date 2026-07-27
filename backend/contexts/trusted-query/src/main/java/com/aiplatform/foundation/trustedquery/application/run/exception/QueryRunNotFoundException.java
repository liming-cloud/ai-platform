package com.aiplatform.foundation.trustedquery.application.run.exception;

import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.trustedquery.domain.common.error.TrustedQueryErrorCode;
/**
 * 查询运行在当前租户中不存在。
 */
public final class QueryRunNotFoundException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public QueryRunNotFoundException() {
        super(TrustedQueryErrorCode.RUN_NOT_FOUND);
    }
}

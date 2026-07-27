package com.aiplatform.foundation.trustedquery.application.run.exception;

import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.trustedquery.domain.common.error.TrustedQueryErrorCode;
/**
 * 当前租户不能使用指定的已发布助手版本。
 */
public final class QueryAssistantUnavailableException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public QueryAssistantUnavailableException() {
        super(TrustedQueryErrorCode.ASSISTANT_UNAVAILABLE);
    }
}

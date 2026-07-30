package com.aiplatform.foundation.trustedquery.application.feedback.exception;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.trustedquery.domain.common.error.TrustedQueryErrorCode;
/** 同一查询运行与请求成员已存在反馈。 */
public final class QueryFeedbackDuplicateException extends ApplicationException {
    private static final long serialVersionUID = 1L;

    public QueryFeedbackDuplicateException() {
        super(TrustedQueryErrorCode.FEEDBACK_EXISTS);
    }
}

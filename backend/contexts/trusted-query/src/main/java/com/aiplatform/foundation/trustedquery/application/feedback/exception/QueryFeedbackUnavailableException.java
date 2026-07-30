package com.aiplatform.foundation.trustedquery.application.feedback.exception;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.trustedquery.domain.common.error.TrustedQueryErrorCode;
/** 查询运行不可用于当前成员提交反馈。 */
public final class QueryFeedbackUnavailableException extends ApplicationException {
    private static final long serialVersionUID = 1L;
    public QueryFeedbackUnavailableException() { super(TrustedQueryErrorCode.RUN_NOT_FOUND); }
}

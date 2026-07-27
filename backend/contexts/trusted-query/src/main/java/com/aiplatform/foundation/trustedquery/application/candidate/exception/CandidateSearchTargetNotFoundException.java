package com.aiplatform.foundation.trustedquery.application.candidate.exception;

import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.trustedquery.domain.common.error.TrustedQueryErrorCode;

/**
 * 当前租户不可见目标知识空间时返回统一资源不存在语义。
 */
public final class CandidateSearchTargetNotFoundException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public CandidateSearchTargetNotFoundException() {
        super(TrustedQueryErrorCode.TARGET_NOT_FOUND);
    }
}

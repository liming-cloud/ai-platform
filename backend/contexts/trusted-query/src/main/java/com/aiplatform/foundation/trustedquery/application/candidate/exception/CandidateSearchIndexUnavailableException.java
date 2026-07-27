package com.aiplatform.foundation.trustedquery.application.candidate.exception;

import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.trustedquery.domain.common.error.TrustedQueryErrorCode;
/**
 * 当前租户的目标知识空间没有可用于查询的活动索引。
 */
public final class CandidateSearchIndexUnavailableException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public CandidateSearchIndexUnavailableException() {
        super(TrustedQueryErrorCode.INDEX_UNAVAILABLE);
    }
}

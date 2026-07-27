package com.aiplatform.foundation.trustedquery.application.candidate.exception;

import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.trustedquery.domain.common.error.TrustedQueryErrorCode;
/**
 * 当前可信主体没有读取知识候选的权限。
 */
public final class CandidateSearchPermissionDeniedException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public CandidateSearchPermissionDeniedException() {
        super(TrustedQueryErrorCode.ACCESS_DENIED);
    }
}

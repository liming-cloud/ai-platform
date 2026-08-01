package com.aiplatform.foundation.trustedquery.application.diagnostic.exception;

import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.trustedquery.domain.common.error.TrustedQueryErrorCode;

/**
 * 当前可信主体不具备读取查询诊断的审计权限。
 */
public final class QueryDiagnosticPermissionDeniedException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public QueryDiagnosticPermissionDeniedException() {
        super(TrustedQueryErrorCode.ACCESS_DENIED);
    }
}

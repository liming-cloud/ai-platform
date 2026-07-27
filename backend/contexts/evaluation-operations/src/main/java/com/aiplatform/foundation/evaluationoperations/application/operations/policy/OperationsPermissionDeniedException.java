package com.aiplatform.foundation.evaluationoperations.application.operations.policy;

import com.aiplatform.foundation.evaluationoperations.domain.common.error.EvaluationOperationsErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 当前成员缺少运营事实读取权限。
 */
public final class OperationsPermissionDeniedException
        extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public OperationsPermissionDeniedException() {
        super(EvaluationOperationsErrorCode.ACCESS_DENIED);
    }
}

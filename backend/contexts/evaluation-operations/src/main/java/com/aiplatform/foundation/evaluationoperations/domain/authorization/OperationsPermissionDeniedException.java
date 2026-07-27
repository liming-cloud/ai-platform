package com.aiplatform.foundation.evaluationoperations.domain.authorization;

/**
 * 当前成员缺少运营事实读取权限。
 */
public final class OperationsPermissionDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OperationsPermissionDeniedException() {
        super("没有读取运营事实的权限");
    }
}

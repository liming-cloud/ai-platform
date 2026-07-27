package com.aiplatform.foundation.knowledgedata.application.knowledge.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 可信主体缺少知识空间操作权限。
 */
public final class KnowledgePermissionDeniedException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public KnowledgePermissionDeniedException() {
        super(KnowledgeDataErrorCode.PERMISSION_DENIED);
    }
}

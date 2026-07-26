package com.aiplatform.foundation.knowledgedata.application.knowledge;

/**
 * 可信主体缺少知识空间操作权限。
 */
public final class KnowledgePermissionDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public KnowledgePermissionDeniedException() {
        super("没有执行该知识操作的权限");
    }
}

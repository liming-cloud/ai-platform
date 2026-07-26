package com.aiplatform.foundation.knowledgedata.application.knowledge;

/**
 * 当前租户已经存在同名知识空间。
 */
public final class KnowledgeSpaceNameConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public KnowledgeSpaceNameConflictException() {
        super("当前租户已经存在同名知识空间");
    }
}

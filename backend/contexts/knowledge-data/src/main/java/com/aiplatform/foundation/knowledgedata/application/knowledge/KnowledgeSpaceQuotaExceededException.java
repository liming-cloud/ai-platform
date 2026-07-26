package com.aiplatform.foundation.knowledgedata.application.knowledge;

/**
 * 当前租户的知识空间数量已经达到批准上限。
 */
public final class KnowledgeSpaceQuotaExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public KnowledgeSpaceQuotaExceededException() {
        super("知识空间数量已经达到租户配额");
    }
}

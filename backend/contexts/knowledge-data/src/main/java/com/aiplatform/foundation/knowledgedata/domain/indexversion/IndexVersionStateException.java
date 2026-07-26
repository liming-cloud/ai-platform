package com.aiplatform.foundation.knowledgedata.domain.indexversion;

/**
 * 索引版本状态不允许执行请求动作。
 */
public final class IndexVersionStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IndexVersionStateException(String message) {
        super(message);
    }
}

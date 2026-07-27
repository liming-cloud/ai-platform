package com.aiplatform.foundation.trustedquery.application.candidate;

/**
 * 当前租户的目标知识空间没有可用于查询的活动索引。
 */
public final class CandidateSearchIndexUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CandidateSearchIndexUnavailableException() {
        super("知识空间没有可用的活动索引");
    }
}

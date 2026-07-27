package com.aiplatform.foundation.trustedquery.application.candidate;

/**
 * 当前可信主体没有读取知识候选的权限。
 */
public final class CandidateSearchPermissionDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CandidateSearchPermissionDeniedException() {
        super("没有读取知识候选的权限");
    }
}

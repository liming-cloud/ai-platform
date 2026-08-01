package com.aiplatform.foundation.trustedquery.application.diagnostic.dto;

/**
 * 候选检索事实的可得性。
 *
 * <p>`NOT_COLLECTED` 仅表示检索前失败，不能被解释为零个候选。</p>
 */
public enum CandidateCollection {
    COLLECTED,
    NOT_COLLECTED
}

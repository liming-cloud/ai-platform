package com.aiplatform.foundation.trustedquery.domain.query.feedback.repository;

import com.aiplatform.foundation.trustedquery.domain.query.feedback.model.QueryFeedback;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.policy.QueryFeedbackRecording;

/** 查询反馈的原子写入端口。 */
public interface QueryFeedbackRepository {
    QueryFeedbackRecording record(QueryFeedback feedback);
}

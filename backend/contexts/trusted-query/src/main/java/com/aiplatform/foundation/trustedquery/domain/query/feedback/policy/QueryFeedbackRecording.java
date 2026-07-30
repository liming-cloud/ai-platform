package com.aiplatform.foundation.trustedquery.domain.query.feedback.policy;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/** 查询反馈原子写入的不可变结果。 */
public record QueryFeedbackRecording(boolean inserted) implements ValueObject {
    public static QueryFeedbackRecording recorded() { return new QueryFeedbackRecording(true); }
    public static QueryFeedbackRecording duplicate() { return new QueryFeedbackRecording(false); }
}

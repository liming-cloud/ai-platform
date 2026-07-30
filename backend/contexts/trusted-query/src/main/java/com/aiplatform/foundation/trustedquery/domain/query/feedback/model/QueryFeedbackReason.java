package com.aiplatform.foundation.trustedquery.domain.query.feedback.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 不含自由文本的受控查询反馈原因。
 */
public enum QueryFeedbackReason implements ValueObject {
    HELPFUL(QueryFeedbackOutcome.POSITIVE),
    INCORRECT(QueryFeedbackOutcome.NEGATIVE),
    INSUFFICIENT(QueryFeedbackOutcome.NEGATIVE),
    IRRELEVANT(QueryFeedbackOutcome.NEGATIVE);

    private final QueryFeedbackOutcome outcome;

    QueryFeedbackReason(QueryFeedbackOutcome outcome) {
        this.outcome = outcome;
    }

    boolean supports(QueryFeedbackOutcome value) {
        return outcome == value;
    }
}

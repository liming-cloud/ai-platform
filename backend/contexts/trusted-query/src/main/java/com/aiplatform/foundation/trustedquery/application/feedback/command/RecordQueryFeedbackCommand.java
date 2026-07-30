package com.aiplatform.foundation.trustedquery.application.feedback.command;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.model.QueryFeedbackOutcome;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.model.QueryFeedbackReason;

/** 记录最小查询反馈的受认证命令。 */
public record RecordQueryFeedbackCommand(TrustedRequestContext context, String queryRunId,
        QueryFeedbackOutcome outcome, QueryFeedbackReason reason) {}

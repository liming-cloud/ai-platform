package com.aiplatform.foundation.trustedquery.application.feedback.service;

import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.trustedquery.application.feedback.command.RecordQueryFeedbackCommand;
import com.aiplatform.foundation.trustedquery.application.feedback.exception.QueryFeedbackDuplicateException;
import com.aiplatform.foundation.trustedquery.application.feedback.exception.QueryFeedbackUnavailableException;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.model.QueryFeedback;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.model.QueryFeedbackId;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.model.QueryFeedbackRequesterId;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.repository.QueryFeedbackRepository;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunStatus;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import java.time.Clock;
import java.util.Objects;

/** 编排已完成查询的最小反馈写入。 */
public final class QueryFeedbackService {
    private static final PermissionCode QUERY = new PermissionCode("assistant:query");
    private final QueryRunRepository runs; private final QueryFeedbackRepository feedback;
    private final com.aiplatform.foundation.trustedquery.application.run.service.QueryRunAuthorization authorization;
    private final Clock clock; private final EntityIdGenerator identifiers;
    public QueryFeedbackService(QueryRunRepository runs, QueryFeedbackRepository feedback, Clock clock, EntityIdGenerator identifiers, com.aiplatform.foundation.trustedquery.application.run.service.QueryRunAuthorization authorization) {
        this.authorization = Objects.requireNonNull(authorization);
        this.runs = Objects.requireNonNull(runs, "查询运行仓储不能为空"); this.feedback = Objects.requireNonNull(feedback, "查询反馈仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空"); this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
    }
    public void record(RecordQueryFeedbackCommand command) {
        Objects.requireNonNull(command, "查询反馈命令不能为空");
        if (!command.context().hasPermission(QUERY)) throw new QueryFeedbackUnavailableException();
        var run = runs.find(command.context().tenantId(), QueryRunId.parse(command.queryRunId())).orElseThrow(QueryFeedbackUnavailableException::new);
        if (run.status() == QueryRunStatus.RUNNING || run.requestedBy().value() != command.context().membershipId().value()) throw new QueryFeedbackUnavailableException();
        authorization.requireHistory(command.context(), run);
        var value = QueryFeedback.record(command.context().tenantId(), new QueryFeedbackId(identifiers.nextId()), run.id(), new QueryFeedbackRequesterId(command.context().membershipId().value()), command.outcome(), command.reason(), clock.instant());
        if (!feedback.record(value).inserted()) throw new QueryFeedbackDuplicateException();
    }
}

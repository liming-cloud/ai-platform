package com.aiplatform.foundation.trustedquery.domain.query.feedback.model;

import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import java.time.Instant;
import java.util.Objects;

/**
 * 请求成员对已完成查询运行提交的一次不可变最小反馈事实。
 *
 * <p>聚合只保留关联标识、受控判断与创建时间；正文和令牌始终留在本次受权交互边界内。</p>
 */
public final class QueryFeedback implements AggregateRoot<QueryFeedbackId> {

    private final TenantId tenantId;
    private final QueryFeedbackId id;
    private final QueryRunId queryRunId;
    private final QueryFeedbackRequesterId requestedBy;
    private final QueryFeedbackOutcome outcome;
    private final QueryFeedbackReason reason;
    private final Instant createdAt;

    private QueryFeedback(
            TenantId tenantId,
            QueryFeedbackId id,
            QueryRunId queryRunId,
            QueryFeedbackRequesterId requestedBy,
            QueryFeedbackOutcome outcome,
            QueryFeedbackReason reason,
            Instant createdAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.id = Objects.requireNonNull(id, "查询反馈标识不能为空");
        this.queryRunId = Objects.requireNonNull(queryRunId, "查询运行标识不能为空");
        this.requestedBy = Objects.requireNonNull(requestedBy, "查询反馈请求成员不能为空");
        this.outcome = Objects.requireNonNull(outcome, "查询反馈结果不能为空");
        this.reason = Objects.requireNonNull(reason, "查询反馈原因不能为空");
        if (!reason.supports(outcome)) {
            throw new IllegalArgumentException("查询反馈原因与正负结果不一致");
        }
        this.createdAt = Objects.requireNonNull(createdAt, "查询反馈创建时间不能为空");
    }

    /**
     * 记录一条不可修改的最小查询反馈。
     *
     * @throws IllegalArgumentException 原因与正负结果不一致时抛出。
     */
    public static QueryFeedback record(
            TenantId tenantId,
            QueryFeedbackId id,
            QueryRunId queryRunId,
            QueryFeedbackRequesterId requestedBy,
            QueryFeedbackOutcome outcome,
            QueryFeedbackReason reason,
            Instant createdAt) {
        return new QueryFeedback(
                tenantId, id, queryRunId, requestedBy, outcome, reason, createdAt);
    }

    public TenantId tenantId() {
        return tenantId;
    }

    @Override
    public QueryFeedbackId id() {
        return id;
    }

    public QueryRunId queryRunId() {
        return queryRunId;
    }

    public QueryFeedbackRequesterId requestedBy() {
        return requestedBy;
    }

    public QueryFeedbackOutcome outcome() {
        return outcome;
    }

    public QueryFeedbackReason reason() {
        return reason;
    }

    public Instant createdAt() {
        return createdAt;
    }
}

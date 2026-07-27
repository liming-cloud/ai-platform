package com.aiplatform.foundation.trustedquery.domain;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelUsage;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 可持久化并按序重放的查询运行状态快照。
 */
public final class QueryRunEvent {

    private final TenantId tenantId;
    private final QueryRunId queryRunId;
    private final long sequence;
    private final QueryRunEventType type;
    private final QueryRunStatus status;
    private final String answer;
    private final QueryRefusalReason refusalReason;
    private final List<QueryCitation> citations;
    private final ModelUsage usage;
    private final Instant occurredAt;

    private QueryRunEvent(
            TenantId tenantId,
            QueryRunId queryRunId,
            long sequence,
            QueryRunEventType type,
            QueryRunStatus status,
            String answer,
            QueryRefusalReason refusalReason,
            List<QueryCitation> citations,
            ModelUsage usage,
            Instant occurredAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.queryRunId = Objects.requireNonNull(queryRunId, "查询运行标识不能为空");
        if (sequence < 1) {
            throw new IllegalArgumentException("查询事件序号必须大于零");
        }
        this.sequence = sequence;
        this.type = Objects.requireNonNull(type, "查询事件类型不能为空");
        this.status = Objects.requireNonNull(status, "查询状态不能为空");
        this.answer = answer;
        this.refusalReason = refusalReason;
        this.citations = List.copyOf(
                Objects.requireNonNull(citations, "查询引用不能为空"));
        this.usage = Objects.requireNonNull(usage, "模型用量不能为空");
        this.occurredAt = Objects.requireNonNull(occurredAt, "事件时间不能为空");
        verifyState();
    }

    public static QueryRunEvent started(QueryRun queryRun) {
        Objects.requireNonNull(queryRun, "查询运行不能为空");
        if (queryRun.status() != QueryRunStatus.RUNNING
                || queryRun.quota() == null) {
            throw new IllegalArgumentException("开始事件只能来自已占用配额的运行中查询");
        }
        return new QueryRunEvent(
                queryRun.tenantId(),
                queryRun.id(),
                1,
                QueryRunEventType.QUERY_STARTED,
                queryRun.status(),
                null,
                null,
                List.of(),
                ModelUsage.UNKNOWN,
                queryRun.createdAt());
    }

    public static QueryRunEvent completed(QueryRun queryRun, long sequence) {
        Objects.requireNonNull(queryRun, "查询运行不能为空");
        if (queryRun.status() == QueryRunStatus.RUNNING) {
            throw new IllegalArgumentException("完成事件不能来自运行中查询");
        }
        return new QueryRunEvent(
                queryRun.tenantId(),
                queryRun.id(),
                sequence,
                typeOf(queryRun.status()),
                queryRun.status(),
                queryRun.answer(),
                queryRun.refusalReason(),
                queryRun.citations(),
                queryRun.usage(),
                queryRun.completedAt());
    }

    public static QueryRunEvent reconstitute(
            TenantId tenantId,
            QueryRunId queryRunId,
            long sequence,
            QueryRunEventType type,
            QueryRunStatus status,
            String answer,
            QueryRefusalReason refusalReason,
            List<QueryCitation> citations,
            ModelUsage usage,
            Instant occurredAt) {
        return new QueryRunEvent(
                tenantId,
                queryRunId,
                sequence,
                type,
                status,
                answer,
                refusalReason,
                citations,
                usage,
                occurredAt);
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public QueryRunId queryRunId() {
        return queryRunId;
    }

    public long sequence() {
        return sequence;
    }

    public QueryRunEventType type() {
        return type;
    }

    public QueryRunStatus status() {
        return status;
    }

    public String answer() {
        return answer;
    }

    public QueryRefusalReason refusalReason() {
        return refusalReason;
    }

    public List<QueryCitation> citations() {
        return citations;
    }

    public ModelUsage usage() {
        return usage;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public boolean terminal() {
        return status != QueryRunStatus.RUNNING;
    }

    private void verifyState() {
        switch (type) {
            case QUERY_STARTED -> require(
                    sequence == 1
                            && status == QueryRunStatus.RUNNING
                            && answer == null
                            && refusalReason == null
                            && citations.isEmpty()
                            && usage.equals(ModelUsage.UNKNOWN));
            case QUERY_ANSWERED -> require(
                    sequence > 1
                            && status == QueryRunStatus.ANSWERED
                            && answer != null
                            && !answer.isBlank()
                            && refusalReason == null
                            && validCitations());
            case QUERY_REFUSED -> require(
                    sequence > 1
                            && status == QueryRunStatus.REFUSED
                            && answer == null
                            && refusalReason != null
                            && citations.isEmpty()
                            && usage.equals(ModelUsage.UNKNOWN));
            case QUERY_FAILED -> require(
                    sequence > 1
                            && status == QueryRunStatus.FAILED
                            && answer == null
                            && refusalReason == null
                            && citations.isEmpty()
                            && usage.equals(ModelUsage.UNKNOWN));
        }
    }

    private boolean validCitations() {
        for (var index = 0; index < citations.size(); index++) {
            if (citations.get(index).sequence() != index + 1) {
                return false;
            }
        }
        return true;
    }

    private static QueryRunEventType typeOf(QueryRunStatus status) {
        return switch (status) {
            case ANSWERED -> QueryRunEventType.QUERY_ANSWERED;
            case REFUSED -> QueryRunEventType.QUERY_REFUSED;
            case FAILED -> QueryRunEventType.QUERY_FAILED;
            case RUNNING -> throw new IllegalArgumentException(
                    "运行中查询没有完成事件");
        };
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("查询事件状态字段不一致");
        }
    }
}

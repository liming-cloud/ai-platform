package com.aiplatform.foundation.trustedquery.domain;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelUsage;
import com.aiplatform.foundation.security.identity.MembershipId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 管理一次查询从占位、回答或拒答到终态的事务边界。
 */
public final class QueryRun {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final int MAX_ANSWER_CODE_POINTS = 32_768;

    private final TenantId tenantId;
    private final QueryRunId id;
    private final long assistantVersionId;
    private final MembershipId requestedBy;
    private final String questionDigest;
    private final QueryQuotaSnapshot quota;
    private final QueryRunStatus status;
    private final String answer;
    private final QueryRefusalReason refusalReason;
    private final List<QueryCitation> citations;
    private final ModelUsage usage;
    private final long version;
    private final Instant createdAt;
    private final Instant completedAt;

    private QueryRun(
            TenantId tenantId,
            QueryRunId id,
            long assistantVersionId,
            MembershipId requestedBy,
            String questionDigest,
            QueryQuotaSnapshot quota,
            QueryRunStatus status,
            String answer,
            QueryRefusalReason refusalReason,
            List<QueryCitation> citations,
            ModelUsage usage,
            long version,
            Instant createdAt,
            Instant completedAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.id = Objects.requireNonNull(id, "查询运行标识不能为空");
        if (assistantVersionId <= 0) {
            throw new IllegalArgumentException("助手版本标识必须是正整数");
        }
        this.assistantVersionId = assistantVersionId;
        this.requestedBy = Objects.requireNonNull(requestedBy, "请求成员不能为空");
        if (questionDigest == null || !SHA256.matcher(questionDigest).matches()) {
            throw new IllegalArgumentException("问题摘要格式不正确");
        }
        this.questionDigest = questionDigest;
        this.quota = quota;
        this.status = Objects.requireNonNull(status, "查询运行状态不能为空");
        this.answer = answer;
        this.refusalReason = refusalReason;
        this.citations = List.copyOf(
                Objects.requireNonNull(citations, "查询引用不能为空"));
        this.usage = Objects.requireNonNull(usage, "模型用量不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("查询运行版本不能为负数");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.completedAt = completedAt;
        verifyState();
    }

    public static QueryRun start(
            TenantId tenantId,
            QueryRunId id,
            long assistantVersionId,
            MembershipId requestedBy,
            String questionDigest,
            Instant createdAt) {
        return new QueryRun(
                tenantId,
                id,
                assistantVersionId,
                requestedBy,
                questionDigest,
                null,
                QueryRunStatus.RUNNING,
                null,
                null,
                List.of(),
                ModelUsage.UNKNOWN,
                0,
                createdAt,
                null);
    }

    public static QueryRun reconstitute(
            TenantId tenantId,
            QueryRunId id,
            long assistantVersionId,
            MembershipId requestedBy,
            String questionDigest,
            QueryQuotaSnapshot quota,
            QueryRunStatus status,
            String answer,
            QueryRefusalReason refusalReason,
            List<QueryCitation> citations,
            ModelUsage usage,
            long version,
            Instant createdAt,
            Instant completedAt) {
        return new QueryRun(
                tenantId,
                id,
                assistantVersionId,
                requestedBy,
                questionDigest,
                Objects.requireNonNull(quota, "已持久化查询的配额快照不能为空"),
                status,
                answer,
                refusalReason,
                citations,
                usage,
                version,
                createdAt,
                completedAt);
    }

    /**
     * 将尚未持久化的查询提案绑定到原子占用得到的配额事实。
     *
     * @throws IllegalStateException 查询已经占用配额或不再处于运行状态
     */
    public QueryRun acceptQuota(QueryQuotaSnapshot snapshot) {
        if (status != QueryRunStatus.RUNNING || quota != null) {
            throw new IllegalStateException("查询只能占用一次配额");
        }
        return new QueryRun(
                tenantId,
                id,
                assistantVersionId,
                requestedBy,
                questionDigest,
                Objects.requireNonNull(snapshot, "配额快照不能为空"),
                status,
                answer,
                refusalReason,
                citations,
                usage,
                version,
                createdAt,
                completedAt);
    }

    public QueryRun answer(
            String content,
            List<QueryCitation> evidence,
            ModelUsage modelUsage,
            Instant time) {
        requireRunning();
        return new QueryRun(
                tenantId,
                id,
                assistantVersionId,
                requestedBy,
                questionDigest,
                quota,
                QueryRunStatus.ANSWERED,
                requireAnswer(content),
                null,
                requireCitations(evidence),
                modelUsage,
                version + 1,
                createdAt,
                Objects.requireNonNull(time, "完成时间不能为空"));
    }

    public QueryRun refuse(QueryRefusalReason reason, Instant time) {
        requireRunning();
        return new QueryRun(
                tenantId,
                id,
                assistantVersionId,
                requestedBy,
                questionDigest,
                quota,
                QueryRunStatus.REFUSED,
                null,
                Objects.requireNonNull(reason, "拒答原因不能为空"),
                List.of(),
                ModelUsage.UNKNOWN,
                version + 1,
                createdAt,
                Objects.requireNonNull(time, "完成时间不能为空"));
    }

    public QueryRun fail(Instant time) {
        requireRunning();
        return new QueryRun(
                tenantId,
                id,
                assistantVersionId,
                requestedBy,
                questionDigest,
                quota,
                QueryRunStatus.FAILED,
                null,
                null,
                List.of(),
                ModelUsage.UNKNOWN,
                version + 1,
                createdAt,
                Objects.requireNonNull(time, "失败时间不能为空"));
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public QueryRunId id() {
        return id;
    }

    public long assistantVersionId() {
        return assistantVersionId;
    }

    public MembershipId requestedBy() {
        return requestedBy;
    }

    public String questionDigest() {
        return questionDigest;
    }

    public QueryQuotaSnapshot quota() {
        return quota;
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

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    private void requireRunning() {
        if (status != QueryRunStatus.RUNNING || quota == null) {
            throw new IllegalStateException("只有已占用配额的运行中查询可以进入终态");
        }
    }

    private void verifyState() {
        switch (status) {
            case RUNNING -> require(
                    answer == null
                            && refusalReason == null
                            && citations.isEmpty()
                            && completedAt == null
                            && version == 0);
            case ANSWERED -> require(
                    quota != null
                            && answer != null
                            && refusalReason == null
                            && !citations.isEmpty()
                            && completedAt != null
                            && version > 0);
            case REFUSED -> require(
                    quota != null
                            && answer == null
                            && refusalReason != null
                            && citations.isEmpty()
                            && completedAt != null
                            && version > 0);
            case FAILED -> require(
                    quota != null
                            && answer == null
                            && refusalReason == null
                            && citations.isEmpty()
                            && completedAt != null
                            && version > 0);
        }
    }

    private static String requireAnswer(String value) {
        Objects.requireNonNull(value, "回答不能为空");
        if (value.isBlank()
                || value.codePointCount(0, value.length()) > MAX_ANSWER_CODE_POINTS) {
            throw new IllegalArgumentException("回答内容格式不正确");
        }
        return value;
    }

    private static List<QueryCitation> requireCitations(List<QueryCitation> values) {
        var copied = List.copyOf(
                Objects.requireNonNull(values, "回答引用不能为空"));
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("可信回答必须至少包含一项引用");
        }
        for (var index = 0; index < copied.size(); index++) {
            if (copied.get(index).sequence() != index + 1) {
                throw new IllegalArgumentException("引用序号必须连续且从一开始");
            }
        }
        return copied;
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("查询运行状态字段不一致");
        }
    }
}

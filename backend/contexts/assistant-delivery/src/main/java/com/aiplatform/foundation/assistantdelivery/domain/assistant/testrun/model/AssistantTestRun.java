package com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model;

import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 保存草稿助手测试最小可审计事实的独立聚合根。
 *
 * <p>它不保存题目、回答或证据正文，也不复用普通 `QueryRun` 的状态、配额和事件。</p>
 */
public final class AssistantTestRun implements AggregateRoot<AssistantTestRunId> {

    private static final Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");

    private final TenantId tenantId;
    private final AssistantTestRunId id;
    private final AssistantTestRequesterId requestedBy;
    private final AssistantTestTarget target;
    private final String questionDigest;
    private final AssistantTestQuotaSnapshot quota;
    private final AssistantTestRunStatus status;
    private final AssistantTestRefusalReason refusalReason;
    private final List<AssistantTestCitation> citations;
    private final AssistantTestUsage usage;
    private final long version;
    private final Instant createdAt;
    private final Instant completedAt;

    private AssistantTestRun(
            TenantId tenantId,
            AssistantTestRunId id,
            AssistantTestRequesterId requestedBy,
            AssistantTestTarget target,
            String questionDigest,
            AssistantTestQuotaSnapshot quota,
            AssistantTestRunStatus status,
            AssistantTestRefusalReason refusalReason,
            List<AssistantTestCitation> citations,
            AssistantTestUsage usage,
            long version,
            Instant createdAt,
            Instant completedAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.id = Objects.requireNonNull(id, "助手测试运行标识不能为空");
        this.requestedBy = Objects.requireNonNull(requestedBy, "助手测试发起成员不能为空");
        this.target = Objects.requireNonNull(target, "助手测试目标不能为空");
        if (questionDigest == null || !SHA256.matcher(questionDigest).matches()) {
            throw new IllegalArgumentException("助手测试问题摘要格式不正确");
        }
        this.questionDigest = questionDigest;
        this.quota = quota;
        this.status = Objects.requireNonNull(status, "助手测试运行状态不能为空");
        this.refusalReason = refusalReason;
        this.citations = List.copyOf(Objects.requireNonNull(citations, "助手测试引用不能为空"));
        this.usage = Objects.requireNonNull(usage, "助手测试模型用量不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("助手测试运行修订不能为负数");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.completedAt = completedAt;
        verifyState();
    }

    /**
     * 创建尚未占用测试配额的草稿测试运行。
     */
    public static AssistantTestRun start(
            TenantId tenantId,
            AssistantTestRunId id,
            AssistantTestRequesterId requestedBy,
            AssistantTestTarget target,
            String questionDigest,
            Instant createdAt) {
        return new AssistantTestRun(
                tenantId,
                id,
                requestedBy,
                target,
                questionDigest,
                null,
                AssistantTestRunStatus.RUNNING,
                null,
                List.of(),
                AssistantTestUsage.UNKNOWN,
                0,
                createdAt,
                null);
    }

    /**
     * 绑定仓储原子占用得到的独立草稿测试配额事实。
     *
     * @throws IllegalStateException 运行已占用配额或已进入终态
     */
    public AssistantTestRun acceptQuota(AssistantTestQuotaSnapshot snapshot) {
        if (status != AssistantTestRunStatus.RUNNING || quota != null) {
            throw new IllegalStateException("助手测试只能占用一次配额");
        }
        return new AssistantTestRun(
                tenantId, id, requestedBy, target, questionDigest,
                Objects.requireNonNull(snapshot, "助手测试配额快照不能为空"), status, refusalReason,
                citations, usage, version, createdAt, completedAt);
    }

    /**
     * 记录已完成引用核验的回答终态，仅保留用量和最小引用标识。
     *
     * @throws IllegalStateException 运行尚未占用配额或已进入终态
     * @throws IllegalArgumentException 引用标识为空或序号不连续
     */
    public AssistantTestRun answer(
            AssistantTestUsage modelUsage,
            List<AssistantTestCitation> evidence,
            Instant time) {
        requireQuotaAcceptedRunning();
        return new AssistantTestRun(
                tenantId, id, requestedBy, target, questionDigest, quota,
                AssistantTestRunStatus.ANSWERED, null, requireCitations(evidence),
                Objects.requireNonNull(modelUsage, "助手测试模型用量不能为空"),
                version + 1, createdAt, Objects.requireNonNull(time, "完成时间不能为空"));
    }

    /**
     * 记录可信证据不足导致的草稿测试拒答终态。
     *
     * @throws IllegalStateException 运行尚未占用配额或已进入终态
     */
    public AssistantTestRun refuse(AssistantTestRefusalReason reason, Instant time) {
        requireQuotaAcceptedRunning();
        return new AssistantTestRun(
                tenantId, id, requestedBy, target, questionDigest, quota,
                AssistantTestRunStatus.REFUSED, Objects.requireNonNull(reason, "拒答原因不能为空"), List.of(),
                AssistantTestUsage.UNKNOWN, version + 1, createdAt,
                Objects.requireNonNull(time, "拒答时间不能为空"));
    }

    /**
     * 记录不暴露外部失败细节的草稿测试失败终态。
     *
     * @throws IllegalStateException 运行尚未占用配额或已进入终态
     */
    public AssistantTestRun fail(Instant time) {
        requireQuotaAcceptedRunning();
        return new AssistantTestRun(
                tenantId, id, requestedBy, target, questionDigest, quota,
                AssistantTestRunStatus.FAILED, null, List.of(), AssistantTestUsage.UNKNOWN,
                version + 1, createdAt, Objects.requireNonNull(time, "失败时间不能为空"));
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public AssistantTestRunId id() {
        return id;
    }

    public AssistantTestRequesterId requestedBy() {
        return requestedBy;
    }

    public AssistantTestTarget target() {
        return target;
    }

    public String questionDigest() {
        return questionDigest;
    }

    public AssistantTestQuotaSnapshot quota() {
        return quota;
    }

    public AssistantTestRunStatus status() {
        return status;
    }

    public AssistantTestRefusalReason refusalReason() {
        return refusalReason;
    }

    public List<AssistantTestCitation> citations() {
        return citations;
    }

    public AssistantTestUsage usage() {
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

    private void requireQuotaAcceptedRunning() {
        if (status != AssistantTestRunStatus.RUNNING || quota == null) {
            throw new IllegalStateException("只有已占用配额的运行中助手测试可以进入终态");
        }
    }

    private void verifyState() {
        switch (status) {
            case RUNNING -> require(
                    refusalReason == null && citations.isEmpty() && usage.equals(AssistantTestUsage.UNKNOWN)
                            && completedAt == null && version == 0);
            case ANSWERED -> require(
                    quota != null && refusalReason == null && !citations.isEmpty() && completedAt != null
                            && version > 0);
            case REFUSED -> require(
                    quota != null && refusalReason != null && citations.isEmpty()
                            && usage.equals(AssistantTestUsage.UNKNOWN) && completedAt != null && version > 0);
            case FAILED -> require(
                    quota != null && refusalReason == null && citations.isEmpty()
                            && usage.equals(AssistantTestUsage.UNKNOWN) && completedAt != null && version > 0);
        }
    }

    private static List<AssistantTestCitation> requireCitations(List<AssistantTestCitation> values) {
        var copied = List.copyOf(Objects.requireNonNull(values, "助手测试引用不能为空"));
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("可信草稿测试回答必须至少包含一项引用");
        }
        for (var index = 0; index < copied.size(); index++) {
            if (copied.get(index).sequence() != index + 1) {
                throw new IllegalArgumentException("助手测试引用序号必须连续且从一开始");
            }
        }
        return copied;
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("助手测试运行状态字段不一致");
        }
    }
}

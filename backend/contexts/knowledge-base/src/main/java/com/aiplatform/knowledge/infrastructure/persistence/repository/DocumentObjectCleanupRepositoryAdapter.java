package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.knowledge.application.maintenance.KnowledgeMaintenanceProperties;
import com.aiplatform.knowledge.domain.StoredDocumentObject;
import com.aiplatform.knowledge.domain.maintenance.ClaimedDocumentObjectCleanupTask;
import com.aiplatform.knowledge.domain.maintenance.DocumentObjectCleanupOwnership;
import com.aiplatform.knowledge.domain.port.DocumentObjectOrphanObservationPort;
import com.aiplatform.knowledge.domain.port.maintenance.DocumentObjectCleanupRepository;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.DocumentObjectCleanupMapper;
import com.aiplatform.knowledge.infrastructure.persistence.projection.DocumentObjectCleanupRow;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DocumentObjectCleanupRepositoryAdapter
        implements DocumentObjectCleanupRepository, DocumentObjectOrphanObservationPort {
    private final DocumentObjectCleanupMapper mapper;
    private final KnowledgeMaintenanceProperties properties;
    private final Clock clock;

    public DocumentObjectCleanupRepositoryAdapter(
            DocumentObjectCleanupMapper mapper,
            KnowledgeMaintenanceProperties properties,
            Clock clock
    ) {
        this.mapper = Objects.requireNonNull(mapper, "对象清理 Mapper 不能为空");
        this.properties = Objects.requireNonNull(properties, "知识维护配置不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void observePotentialOrphan(
            TenantId tenantId,
            String knowledgeBaseId,
            String documentId,
            StoredDocumentObject storedObject,
            String failureType
    ) {
        Instant now = Instant.now(clock);
        observe(
                tenantId, knowledgeBaseId, documentId, storedObject, failureType,
                now, now.plus(properties.getOrphanGracePeriod())
        );
    }

    @Override
    public void observe(
            TenantId tenantId,
            String knowledgeBaseId,
            String documentId,
            StoredDocumentObject storedObject,
            String failureType,
            Instant observedAt,
            Instant firstAttemptAt
    ) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        Objects.requireNonNull(storedObject, "已存储对象不能为空");
        Instant requiredObservedAt = Objects.requireNonNull(observedAt, "观测时间不能为空");
        Instant requiredFirstAttemptAt = Objects.requireNonNull(firstAttemptAt, "首次清理时间不能为空");
        if (requiredFirstAttemptAt.isBefore(requiredObservedAt)) {
            throw new IllegalArgumentException("首次清理时间不能早于观测时间");
        }
        int inserted = mapper.observe(
                id(tenantId.value(), "租户 ID"),
                id(knowledgeBaseId, "知识库 ID"),
                id(documentId, "文档 ID"),
                requireText(storedObject.bucket(), "对象 bucket"),
                requireText(storedObject.objectKey(), "对象 key"),
                requireText(storedObject.sourceUri(), "对象 URI"),
                normalizeFailureType(failureType),
                requiredObservedAt,
                requiredFirstAttemptAt
        );
        if (inserted != 0 && inserted != 1) {
            throw new IllegalStateException("孤儿对象登记返回了非法影响行数");
        }
    }

    @Override
    public Optional<ClaimedDocumentObjectCleanupTask> claimNext(ClaimRequest request) {
        Objects.requireNonNull(request, "对象清理 claim 请求不能为空");
        return Optional.ofNullable(mapper.claimNext(
                request.workerId(), request.leaseToken(), request.now(), request.leaseUntil(), request.maxAttempts()
        )).map(DocumentObjectCleanupRepositoryAdapter::toDomain);
    }

    @Override
    public boolean hasLiveDocumentReference(ClaimedDocumentObjectCleanupTask claimed, Instant now) {
        Objects.requireNonNull(claimed, "已认领对象清理任务不能为空");
        DocumentObjectCleanupOwnership ownership = claimed.ownership();
        Boolean referenced = mapper.hasLiveDocumentReference(
                id(ownership.tenantId().value(), "租户 ID"),
                id(ownership.cleanupId(), "清理任务 ID"),
                ownership.leaseOwner(),
                ownership.leaseToken(),
                ownership.fenceVersion(),
                Objects.requireNonNull(now, "引用校验时间不能为空")
        );
        if (referenced == null) {
            throw new IllegalStateException("对象清理租约已失效或 fence 不匹配");
        }
        return referenced;
    }

    @Override
    public void completeOwned(DocumentObjectCleanupOwnership ownership, Instant now) {
        requireMutation(mapper.completeOwned(identity(ownership), cleanupId(ownership), ownership.leaseOwner(),
                ownership.leaseToken(), ownership.fenceVersion(), requiredNow(now)), "完成");
    }

    @Override
    public void retainOwned(DocumentObjectCleanupOwnership ownership, Instant now) {
        requireMutation(mapper.retainOwned(identity(ownership), cleanupId(ownership), ownership.leaseOwner(),
                ownership.leaseToken(), ownership.fenceVersion(), requiredNow(now)), "保留活引用对象");
    }

    @Override
    public void retryOwned(
            DocumentObjectCleanupOwnership ownership,
            String failureCode,
            String failureSummary,
            Instant nextRetryAt,
            Instant now
    ) {
        Instant requiredNow = requiredNow(now);
        Instant requiredNextRetryAt = Objects.requireNonNull(nextRetryAt, "下次重试时间不能为空");
        if (!requiredNextRetryAt.isAfter(requiredNow)) {
            throw new IllegalArgumentException("下次重试时间必须晚于失败时间");
        }
        requireMutation(mapper.retryOwned(
                identity(ownership), cleanupId(ownership), ownership.leaseOwner(), ownership.leaseToken(),
                ownership.fenceVersion(), requireText(failureCode, "失败码"),
                requireText(failureSummary, "失败摘要"), requiredNextRetryAt, requiredNow
        ), "进入重试等待");
    }

    @Override
    public void failOwned(
            DocumentObjectCleanupOwnership ownership,
            String failureCode,
            String failureSummary,
            Instant now
    ) {
        requireMutation(mapper.failOwned(
                identity(ownership), cleanupId(ownership), ownership.leaseOwner(), ownership.leaseToken(),
                ownership.fenceVersion(), requireText(failureCode, "失败码"),
                requireText(failureSummary, "失败摘要"), requiredNow(now)
        ), "标记失败");
    }

    private static ClaimedDocumentObjectCleanupTask toDomain(DocumentObjectCleanupRow row) {
        TenantId tenantId = TenantId.of(required(row.getTenantId(), "租户 ID"));
        String cleanupId = required(row.getId(), "清理任务 ID");
        int attemptCount = requiredPositive(row.getAttemptCount(), "尝试次数");
        DocumentObjectCleanupOwnership ownership = new DocumentObjectCleanupOwnership(
                tenantId,
                cleanupId,
                requireText(row.getLeaseOwner(), "清理 worker ID"),
                requireText(row.getLeaseToken(), "清理 lease token"),
                requiredPositive(row.getVersion(), "fence version")
        );
        return new ClaimedDocumentObjectCleanupTask(
                tenantId,
                cleanupId,
                required(row.getKnowledgeBaseId(), "知识库 ID"),
                required(row.getDocumentId(), "文档 ID"),
                requireText(row.getBucket(), "对象 bucket"),
                requireText(row.getObjectKey(), "对象 key"),
                requireText(row.getSourceUri(), "对象 URI"),
                requireText(row.getObservationFailureType(), "观测失败类型"),
                attemptCount,
                ownership
        );
    }

    private static long identity(DocumentObjectCleanupOwnership ownership) {
        Objects.requireNonNull(ownership, "对象清理 ownership 不能为空");
        return id(ownership.tenantId().value(), "租户 ID");
    }

    private static long cleanupId(DocumentObjectCleanupOwnership ownership) {
        Objects.requireNonNull(ownership, "对象清理 ownership 不能为空");
        return id(ownership.cleanupId(), "清理任务 ID");
    }

    private static Instant requiredNow(Instant now) {
        return Objects.requireNonNull(now, "对象清理状态时间不能为空");
    }

    private static long id(String value, String fieldName) {
        return parseRequiredLong(value, fieldName);
    }

    private static String required(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("对象清理记录缺少" + fieldName);
        }
        return String.valueOf(value);
    }

    private static int requiredPositive(Integer value, String fieldName) {
        if (value == null || value < 1) {
            throw new IllegalStateException("对象清理记录缺少" + fieldName);
        }
        return value;
    }

    private static String normalizeFailureType(String value) {
        if (value == null || value.isBlank()) {
            return "UnknownFailure";
        }
        String normalized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return normalized.length() > 96 ? normalized.substring(0, 96) : normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static void requireMutation(int updated, String action) {
        if (updated != 1) {
            throw new IllegalStateException("对象清理任务" + action + "失败：租约已失效或 fence 不匹配");
        }
    }
}

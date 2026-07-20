package com.aiplatform.knowledge.application.maintenance;

import com.aiplatform.knowledge.domain.maintenance.ClaimedDocumentObjectCleanupTask;
import com.aiplatform.knowledge.domain.port.maintenance.DocumentObjectCleanupRepository;
import com.aiplatform.knowledge.domain.port.maintenance.DocumentObjectDeletionPort;
import com.aiplatform.knowledge.domain.port.maintenance.KnowledgeIndexMaintenancePort;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Durable 孤儿对象清理与旧索引回收的唯一执行入口。 */
@Service
public class KnowledgeDocumentMaintenanceWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeDocumentMaintenanceWorker.class);
    private static final String TRANSIENT_CODE = "AIP-KB-OBJECT-CLEANUP-TRANSIENT";
    private static final String EXHAUSTED_CODE = "AIP-KB-OBJECT-CLEANUP-ATTEMPTS-EXHAUSTED";

    private final DocumentObjectCleanupRepository cleanups;
    private final DocumentObjectDeletionPort objects;
    private final KnowledgeIndexMaintenancePort indexes;
    private final KnowledgeMaintenanceProperties properties;
    private final Clock clock;
    private final String workerId;
    private final AtomicBoolean draining = new AtomicBoolean();

    @Autowired
    public KnowledgeDocumentMaintenanceWorker(
            DocumentObjectCleanupRepository cleanups,
            DocumentObjectDeletionPort objects,
            KnowledgeIndexMaintenancePort indexes,
            KnowledgeMaintenanceProperties properties,
            Clock clock
    ) {
        this(cleanups, objects, indexes, properties, clock, runtimeWorkerId());
    }

    KnowledgeDocumentMaintenanceWorker(
            DocumentObjectCleanupRepository cleanups,
            DocumentObjectDeletionPort objects,
            KnowledgeIndexMaintenancePort indexes,
            KnowledgeMaintenanceProperties properties,
            Clock clock,
            String workerId
    ) {
        this.cleanups = Objects.requireNonNull(cleanups, "对象清理仓储不能为空");
        this.objects = Objects.requireNonNull(objects, "对象删除端口不能为空");
        this.indexes = Objects.requireNonNull(indexes, "索引维护端口不能为空");
        this.properties = Objects.requireNonNull(properties, "维护配置不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
        this.workerId = requireText(workerId, "维护 worker ID");
    }

    public int drainAvailable() {
        if (!properties.isEnabled() || !draining.compareAndSet(false, true)) {
            return 0;
        }
        int processed = 0;
        try {
            while (processed < properties.getBatchSize()) {
                Optional<ClaimedDocumentObjectCleanupTask> next = claim();
                if (next.isEmpty()) {
                    break;
                }
                execute(next.get());
                processed++;
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("孤儿对象维护轮询失败，将由下一轮重试：workerId={}", workerId, failure);
        } finally {
            cleanupObsoleteIndexes();
            draining.set(false);
        }
        return processed;
    }

    private Optional<ClaimedDocumentObjectCleanupTask> claim() {
        Instant now = Instant.now(clock);
        return cleanups.claimNext(new DocumentObjectCleanupRepository.ClaimRequest(
                workerId,
                UUID.randomUUID().toString(),
                now,
                now.plus(properties.getLeaseDuration()),
                properties.getMaxAttempts()
        ));
    }

    private void execute(ClaimedDocumentObjectCleanupTask claimed) {
        if (claimed.attemptCount() > properties.getMaxAttempts()) {
            fail(claimed, EXHAUSTED_CODE, "上一次最大次数清理在租约到期前未完成");
            return;
        }
        Instant ownershipCheckedAt = Instant.now(clock);
        try {
            if (cleanups.hasLiveDocumentReference(claimed, ownershipCheckedAt)) {
                cleanups.retainOwned(claimed.ownership(), Instant.now(clock));
                LOGGER.info(
                        "孤儿对象清理因活跃文档引用而保留：tenantId={}, knowledgeBaseId={}, documentId={}, cleanupId={}",
                        claimed.tenantId().value(), claimed.knowledgeBaseId(), claimed.documentId(), claimed.cleanupId()
                );
                return;
            }
            objects.delete(claimed);
            cleanups.completeOwned(claimed.ownership(), Instant.now(clock));
            LOGGER.info(
                    "孤儿对象清理完成：tenantId={}, knowledgeBaseId={}, documentId={}, cleanupId={}",
                    claimed.tenantId().value(), claimed.knowledgeBaseId(), claimed.documentId(), claimed.cleanupId()
            );
        } catch (RuntimeException failure) {
            String summary = safeSummary(failure);
            if (claimed.attemptCount() < properties.getMaxAttempts()) {
                Instant failedAt = Instant.now(clock);
                try {
                    cleanups.retryOwned(
                            claimed.ownership(), TRANSIENT_CODE, summary,
                            failedAt.plus(backoff(claimed.attemptCount())), failedAt
                    );
                } catch (RuntimeException fenceFailure) {
                    logFenceFailure(claimed, fenceFailure);
                }
                return;
            }
            fail(claimed, TRANSIENT_CODE, summary);
        }
    }

    private void fail(ClaimedDocumentObjectCleanupTask claimed, String code, String summary) {
        try {
            cleanups.failOwned(claimed.ownership(), code, summary, Instant.now(clock));
        } catch (RuntimeException fenceFailure) {
            logFenceFailure(claimed, fenceFailure);
        }
    }

    private void cleanupObsoleteIndexes() {
        Instant cutoff = Instant.now(clock).minus(properties.getObsoleteIndexRetention());
        try {
            int deleted = indexes.deleteObsoleteEmbeddings(cutoff, properties.getBatchSize());
            if (deleted > 0) {
                LOGGER.info("旧索引候选向量清理完成：deletedRows={}, updatedBefore={}", deleted, cutoff);
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("旧索引候选向量清理失败，将由下一轮幂等重试：updatedBefore={}", cutoff, failure);
        }
    }

    private Duration backoff(int attemptCount) {
        long multiplier = 1L << Math.min(20, Math.max(0, attemptCount - 1));
        try {
            return properties.getInitialBackoff().multipliedBy(multiplier);
        } catch (ArithmeticException ignored) {
            return Duration.ofHours(24);
        }
    }

    private static String safeSummary(Throwable failure) {
        String message = failure.getMessage();
        String source = message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
        String sanitized = source
                .replaceAll("sk-[A-Za-z0-9_\\-]+", "sk-***")
                .replaceAll("(?i)(api[_-]?key|token|secret)\\s*[=:]\\s*[^\\s,;]+", "$1=***")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        return sanitized.length() > 240 ? sanitized.substring(0, 240) : sanitized;
    }

    private static void logFenceFailure(ClaimedDocumentObjectCleanupTask claimed, RuntimeException failure) {
        LOGGER.warn(
                "对象清理状态未落库，lease 可能已被接管：tenantId={}, cleanupId={}, attempt={}",
                claimed.tenantId().value(), claimed.cleanupId(), claimed.attemptCount(), failure
        );
    }

    private static String runtimeWorkerId() {
        try {
            return "kb-maintenance-" + InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (UnknownHostException ignored) {
            return "kb-maintenance-" + UUID.randomUUID();
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

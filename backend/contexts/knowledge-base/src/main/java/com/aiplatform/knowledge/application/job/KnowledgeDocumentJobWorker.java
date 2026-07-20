package com.aiplatform.knowledge.application.job;

import com.aiplatform.knowledge.domain.ClaimedDocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
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

/**
 * 数据库作业事实的唯一执行入口。MQ 只能唤醒本 worker，不能替代 claim/lease/fence。
 */
@Service
public class KnowledgeDocumentJobWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeDocumentJobWorker.class);
    private static final String TRANSIENT_CODE = "AIP-KB-JOB-TRANSIENT";
    private static final String EXHAUSTED_CODE = "AIP-KB-JOB-ATTEMPTS-EXHAUSTED";

    private final DocumentIngestJobRepository jobs;
    private final KnowledgeDocumentParseJobExecutor parseExecutor;
    private final KnowledgeDocumentIndexJobExecutor indexExecutor;
    private final KnowledgeDocumentJobTransactionService transactions;
    private final KnowledgeDocumentJobProperties properties;
    private final KnowledgeDocumentJobLeaseHeartbeat heartbeat;
    private final Clock clock;
    private final String workerId;
    private final AtomicBoolean draining = new AtomicBoolean();

    @Autowired
    public KnowledgeDocumentJobWorker(
            DocumentIngestJobRepository jobs,
            KnowledgeDocumentParseJobExecutor parseExecutor,
            KnowledgeDocumentIndexJobExecutor indexExecutor,
            KnowledgeDocumentJobTransactionService transactions,
            KnowledgeDocumentJobProperties properties,
            KnowledgeDocumentJobLeaseHeartbeat heartbeat,
            Clock clock
    ) {
        this(jobs, parseExecutor, indexExecutor, transactions, properties, heartbeat, clock, runtimeWorkerId());
    }

    KnowledgeDocumentJobWorker(
            DocumentIngestJobRepository jobs,
            KnowledgeDocumentParseJobExecutor parseExecutor,
            KnowledgeDocumentIndexJobExecutor indexExecutor,
            KnowledgeDocumentJobTransactionService transactions,
            KnowledgeDocumentJobProperties properties,
            KnowledgeDocumentJobLeaseHeartbeat heartbeat,
            Clock clock,
            String workerId
    ) {
        this.jobs = Objects.requireNonNull(jobs, "文档任务仓储不能为空");
        this.parseExecutor = Objects.requireNonNull(parseExecutor, "解析执行器不能为空");
        this.indexExecutor = Objects.requireNonNull(indexExecutor, "索引执行器不能为空");
        this.transactions = Objects.requireNonNull(transactions, "文档作业短事务服务不能为空");
        this.properties = Objects.requireNonNull(properties, "文档作业配置不能为空");
        this.heartbeat = Objects.requireNonNull(heartbeat, "文档作业续租器不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
        this.workerId = requireText(workerId, "worker ID");
    }

    public int drainAvailable() {
        if (!properties.isEnabled() || !draining.compareAndSet(false, true)) {
            return 0;
        }
        try {
            int processed = 0;
            while (processed < properties.getBatchSize()) {
                Optional<ClaimedDocumentIngestJob> next = claim();
                if (next.isEmpty()) {
                    break;
                }
                execute(next.get());
                processed++;
            }
            return processed;
        } finally {
            draining.set(false);
        }
    }

    private Optional<ClaimedDocumentIngestJob> claim() {
        Instant now = Instant.now(clock);
        return jobs.claimNext(new DocumentIngestJobRepository.ClaimRequest(
                workerId,
                UUID.randomUUID().toString(),
                now,
                now.plus(properties.getLeaseDuration()),
                properties.getMaxAttempts()
        ));
    }

    private void execute(ClaimedDocumentIngestJob claimed) {
        if (claimed.attemptCount() > properties.getMaxAttempts()) {
            fail(claimed, EXHAUSTED_CODE, "上一次最大次数尝试在租约到期前未完成");
            return;
        }
        try (var ignored = heartbeat.keepAlive(claimed)) {
            switch (claimed.job().jobType()) {
                case DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE -> parseExecutor.execute(claimed);
                case DocumentIngestJob.JOB_TYPE_DOCUMENT_INDEX -> indexExecutor.execute(claimed);
                default -> throw KnowledgeDocumentJobFailure.terminal(
                        "AIP-KB-JOB-TYPE", "不支持的文档作业类型"
                );
            }
        } catch (KnowledgeDocumentJobFailure failure) {
            handleFailure(claimed, failure.failureCode(), safeSummary(failure), failure.retryable());
        } catch (RuntimeException failure) {
            handleFailure(claimed, TRANSIENT_CODE, safeSummary(failure), true);
        }
    }

    private void handleFailure(
            ClaimedDocumentIngestJob claimed,
            String code,
            String summary,
            boolean retryable
    ) {
        if (retryable && claimed.attemptCount() < properties.getMaxAttempts()) {
            Instant now = Instant.now(clock);
            try {
                transactions.retry(claimed, code, summary, now.plus(backoff(claimed.attemptCount())), now);
            } catch (RuntimeException fenceFailure) {
                LOGGER.warn(
                        "文档作业重试状态未落库，租约可能已被接管：tenantId={}, jobId={}, attempt={}",
                        claimed.job().tenantId().value(), claimed.job().jobId(), claimed.attemptCount(), fenceFailure
                );
            }
            return;
        }
        fail(claimed, code, summary);
    }

    private void fail(ClaimedDocumentIngestJob claimed, String code, String summary) {
        Instant now = Instant.now(clock);
        try {
            transactions.fail(claimed, code, summary, now);
        } catch (RuntimeException fenceFailure) {
            LOGGER.warn(
                    "文档作业失败状态未落库，租约可能已被接管：tenantId={}, jobId={}, attempt={}",
                    claimed.job().tenantId().value(), claimed.job().jobId(), claimed.attemptCount(), fenceFailure
            );
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
        return safeSummary(source);
    }

    private static String safeSummary(String source) {
        String sanitized = source
                .replaceAll("sk-[A-Za-z0-9_\\-]+", "sk-***")
                .replaceAll("(?i)(api[_-]?key|token|secret)\\s*[=:]\\s*[^\\s,;]+", "$1=***")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        if (sanitized.isEmpty()) {
            sanitized = "未提供失败摘要";
        }
        return sanitized.length() > 240 ? sanitized.substring(0, 240) : sanitized;
    }

    private static String runtimeWorkerId() {
        try {
            return "kb-" + InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (UnknownHostException ignored) {
            return "kb-worker-" + UUID.randomUUID();
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

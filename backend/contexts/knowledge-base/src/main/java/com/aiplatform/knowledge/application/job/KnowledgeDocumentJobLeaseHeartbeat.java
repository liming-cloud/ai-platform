package com.aiplatform.knowledge.application.job;

import com.aiplatform.knowledge.domain.ClaimedDocumentIngestJob;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 在外部解析与 embedding 调用期间续租，数据库 fence 仍是迟到 worker 的最终写入边界。
 */
@Service
public class KnowledgeDocumentJobLeaseHeartbeat implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeDocumentJobLeaseHeartbeat.class);

    private final DocumentIngestJobRepository jobs;
    private final KnowledgeDocumentJobProperties properties;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;

    @Autowired
    public KnowledgeDocumentJobLeaseHeartbeat(
            DocumentIngestJobRepository jobs,
            KnowledgeDocumentJobProperties properties,
            Clock clock
    ) {
        this(jobs, properties, clock, Executors.newSingleThreadScheduledExecutor(daemonThreadFactory()));
    }

    KnowledgeDocumentJobLeaseHeartbeat(
            DocumentIngestJobRepository jobs,
            KnowledgeDocumentJobProperties properties,
            Clock clock,
            ScheduledExecutorService scheduler
    ) {
        this.jobs = Objects.requireNonNull(jobs, "文档任务仓储不能为空");
        this.properties = Objects.requireNonNull(properties, "文档任务配置不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
        this.scheduler = Objects.requireNonNull(scheduler, "续租调度器不能为空");
    }

    public KnowledgeDocumentJobLeaseHandle keepAlive(ClaimedDocumentIngestJob claimed) {
        Objects.requireNonNull(claimed, "已认领任务不能为空");
        Duration interval = heartbeatInterval(properties.getLeaseDuration());
        AtomicBoolean closed = new AtomicBoolean();
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                () -> renew(claimed, closed),
                interval.toNanos(),
                interval.toNanos(),
                TimeUnit.NANOSECONDS
        );
        return new KnowledgeDocumentJobLeaseHandle(closed, future);
    }

    private void renew(ClaimedDocumentIngestJob claimed, AtomicBoolean closed) {
        if (closed.get()) {
            return;
        }
        Instant now = Instant.now(clock);
        try {
            jobs.renewOwned(claimed.ownership(), now.plus(properties.getLeaseDuration()), now);
        } catch (RuntimeException failure) {
            if (!closed.get()) {
                LOGGER.warn(
                        "文档作业 lease heartbeat 失败，将继续按 fence 失败关闭：tenantId={}, jobId={}, attempt={}",
                        claimed.job().tenantId().value(), claimed.job().jobId(), claimed.attemptCount(), failure
                );
            }
        }
    }

    private static Duration heartbeatInterval(Duration leaseDuration) {
        Duration requiredLease = Objects.requireNonNull(leaseDuration, "任务租约时长不能为空");
        if (requiredLease.isZero() || requiredLease.isNegative()) {
            throw new IllegalArgumentException("任务租约时长必须大于 0");
        }
        Duration interval = requiredLease.dividedBy(3);
        return interval.isZero() ? Duration.ofNanos(1) : interval;
    }

    @Override
    @PreDestroy
    public void close() {
        scheduler.shutdownNow();
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "knowledge-job-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        };
    }

}

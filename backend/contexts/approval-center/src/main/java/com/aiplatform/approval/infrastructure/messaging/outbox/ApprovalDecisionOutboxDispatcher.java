package com.aiplatform.approval.infrastructure.messaging.outbox;

import com.aiplatform.approval.infrastructure.persistence.mapper.ApprovalDecisionOutboxMapper;
import com.aiplatform.infra.rocketmq.publisher.DomainEventPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 在审批业务事务提交后，以 lease fencing 和无限有界退避执行至少一次投递。
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "spring.cloud.stream.bindings.domainEvent-out-0.destination")
public class ApprovalDecisionOutboxDispatcher {
    private final ApprovalDecisionOutboxMapper mapper;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final String workerId;
    private final Duration leaseDuration;
    private final Duration baseBackoff;
    private final Duration maxBackoff;
    private final int batchSize;

    public ApprovalDecisionOutboxDispatcher(
            ApprovalDecisionOutboxMapper mapper,
            DomainEventPublisher events,
            Clock clock,
            @Value("${aip.approval.decision-outbox.worker-id:${random.uuid}}") String workerId,
            @Value("${aip.approval.decision-outbox.lease-duration:PT30S}") Duration leaseDuration,
            @Value("${aip.approval.decision-outbox.base-backoff:PT5S}") Duration baseBackoff,
            @Value("${aip.approval.decision-outbox.max-backoff:PT5M}") Duration maxBackoff,
            @Value("${aip.approval.decision-outbox.batch-size:20}") int batchSize
    ) {
        this.mapper = Objects.requireNonNull(mapper, "审批决策 outbox Mapper 不能为空");
        this.events = Objects.requireNonNull(events, "领域事件发布器不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
        this.workerId = requireText(workerId, "outbox worker ID");
        this.leaseDuration = requirePositive(leaseDuration, "outbox lease 时长");
        this.baseBackoff = requirePositive(baseBackoff, "outbox 基础退避时长");
        this.maxBackoff = requirePositive(maxBackoff, "outbox 最大退避时长");
        if (this.maxBackoff.compareTo(this.baseBackoff) < 0) {
            throw new IllegalArgumentException("outbox 最大退避时长不能小于基础退避时长");
        }
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("outbox batch size 必须位于 1..100");
        }
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${aip.approval.decision-outbox.poll-delay-ms:1000}")
    public void scheduledDispatch() {
        dispatchBatch();
    }

    public int dispatchBatch() {
        Instant claimedAt = Instant.now(clock);
        List<ApprovalDecisionOutboxRecord> claimed = mapper.claimBatch(
                workerId,
                claimedAt,
                claimedAt.plus(leaseDuration),
                batchSize
        );
        if (claimed == null) {
            throw new IllegalStateException("审批决策 outbox claim 结果不能为空");
        }
        int published = 0;
        for (ApprovalDecisionOutboxRecord record : claimed) {
            published += dispatchOne(Objects.requireNonNull(record, "审批决策 outbox 记录不能为空"));
        }
        return published;
    }

    private int dispatchOne(ApprovalDecisionOutboxRecord record) {
        try {
            events.publish(record.toDomainEventMessage());
            Instant completedAt = Instant.now(clock);
            requireAffectedOne(
                    mapper.markPublished(record.requiredId(), workerId, completedAt),
                    "审批决策 outbox 发布完成状态写入失败"
            );
            return 1;
        } catch (RuntimeException exception) {
            Instant failedAt = Instant.now(clock);
            Instant retryAt = failedAt.plus(backoff(record.requiredAttempts()));
            requireAffectedOne(
                    mapper.markRetry(
                            record.requiredId(),
                            workerId,
                            failedAt,
                            retryAt,
                            sanitizedFailureType(exception)
                    ),
                    "审批决策 outbox 重试状态写入失败"
            );
            return 0;
        }
    }

    private Duration backoff(int attempts) {
        Duration delay = baseBackoff;
        for (int currentAttempt = 1;
                currentAttempt < attempts && delay.compareTo(maxBackoff) < 0;
                currentAttempt++) {
            Duration doubled = delay.multipliedBy(2);
            delay = doubled.compareTo(maxBackoff) > 0 ? maxBackoff : doubled;
        }
        return delay;
    }

    private static String sanitizedFailureType(RuntimeException exception) {
        String simpleName = exception.getClass().getSimpleName();
        return simpleName.isBlank() ? "PublishFailure" : simpleName;
    }

    private static void requireAffectedOne(int affected, String message) {
        if (affected != 1) {
            throw new IllegalStateException(message);
        }
    }

    private static Duration requirePositive(Duration value, String fieldName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

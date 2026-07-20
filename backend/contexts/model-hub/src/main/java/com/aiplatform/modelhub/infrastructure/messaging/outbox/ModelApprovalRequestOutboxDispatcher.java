package com.aiplatform.modelhub.infrastructure.messaging.outbox;

import com.aiplatform.infra.rocketmq.publisher.DomainEventPublisher;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelApprovalRequestOutboxMapper;
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
 * 在业务事务提交后 claim 模型审批请求，并以 lease fencing 执行至少一次消息投递。
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "spring.cloud.stream.bindings.domainEvent-out-0.destination")
public class ModelApprovalRequestOutboxDispatcher {
    private final ModelApprovalRequestOutboxMapper mapper;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final String workerId;
    private final Duration leaseDuration;
    private final Duration baseBackoff;
    private final Duration maxBackoff;
    private final int batchSize;

    public ModelApprovalRequestOutboxDispatcher(
            ModelApprovalRequestOutboxMapper mapper,
            DomainEventPublisher events,
            Clock clock,
            @Value("${aip.model-hub.approval-request-outbox.worker-id:${random.uuid}}") String workerId,
            @Value("${aip.model-hub.approval-request-outbox.lease-duration:PT30S}") Duration leaseDuration,
            @Value("${aip.model-hub.approval-request-outbox.base-backoff:PT5S}") Duration baseBackoff,
            @Value("${aip.model-hub.approval-request-outbox.max-backoff:PT5M}") Duration maxBackoff,
            @Value("${aip.model-hub.approval-request-outbox.batch-size:20}") int batchSize
    ) {
        this.mapper = Objects.requireNonNull(mapper, "模型审批请求 outbox Mapper 不能为空");
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

    @Scheduled(fixedDelayString = "${aip.model-hub.approval-request-outbox.poll-delay-ms:1000}")
    public void scheduledDispatch() {
        dispatchBatch();
    }

    public int dispatchBatch() {
        Instant claimedAt = Instant.now(clock);
        List<ModelApprovalRequestOutboxRecord> claimed = mapper.claimBatch(
                workerId,
                claimedAt,
                claimedAt.plus(leaseDuration),
                batchSize
        );
        if (claimed == null) {
            throw new IllegalStateException("outbox claim 结果不能为空");
        }
        int published = 0;
        for (ModelApprovalRequestOutboxRecord record : claimed) {
            published += dispatchOne(Objects.requireNonNull(record, "outbox claim 记录不能为空"));
        }
        return published;
    }

    private int dispatchOne(ModelApprovalRequestOutboxRecord record) {
        Instant completedAt = Instant.now(clock);
        try {
            events.publish(record.toDomainEventMessage());
            requireAffectedOne(
                    mapper.markPublished(record.requiredId(), workerId, completedAt),
                    "outbox 发布完成状态写入失败"
            );
            return 1;
        } catch (RuntimeException exception) {
            Instant retryAt = completedAt.plus(backoff(record.requiredAttempts()));
            requireAffectedOne(
                    mapper.markRetry(
                            record.requiredId(),
                            workerId,
                            completedAt,
                            retryAt,
                            sanitizedFailureType(exception)
                    ),
                    "outbox 重试状态写入失败"
            );
            return 0;
        }
    }

    private Duration backoff(int attempts) {
        Duration delay = baseBackoff;
        for (int current = 1; current < attempts && delay.compareTo(maxBackoff) < 0; current++) {
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

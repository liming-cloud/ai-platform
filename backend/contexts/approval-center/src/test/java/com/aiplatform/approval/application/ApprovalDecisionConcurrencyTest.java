package com.aiplatform.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static com.aiplatform.approval.support.ApprovalEvidenceTestFixtures.evidence;
import static com.aiplatform.approval.support.ApprovalEvidenceTestFixtures.targetDigest;

import com.aiplatform.approval.application.command.DecideApprovalTaskCommand;
import com.aiplatform.approval.application.error.ApprovalErrorCodes;
import com.aiplatform.approval.domain.event.ApprovalDecisionRecordedEvent;
import com.aiplatform.approval.domain.model.ApprovalDecisionRecord;
import com.aiplatform.approval.domain.model.ApprovalRiskLevel;
import com.aiplatform.approval.domain.model.ApprovalStatus;
import com.aiplatform.approval.domain.model.ApprovalTask;
import com.aiplatform.approval.domain.model.ApprovalTaskPage;
import com.aiplatform.approval.domain.model.ApprovalTaskQuery;
import com.aiplatform.approval.domain.port.ApprovalDecisionEventPublisher;
import com.aiplatform.approval.domain.port.ApprovalTaskDecisionConflictException;
import com.aiplatform.approval.domain.port.ApprovalTaskRepository;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ApprovalDecisionConcurrencyTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");

    @Test
    void oppositeConcurrentDecisionsProduceOneSuccessOneRecordAndOneOutboxEvent() throws Exception {
        ConcurrentRepository repository = new ConcurrentRepository(pendingTask());
        RecordingOutbox events = new RecordingOutbox();
        ApprovalTaskApplicationService service = new ApprovalTaskApplicationService(
                repository,
                events,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> approve = executor.submit(() -> decide(
                    service,
                    new DecideApprovalTaskCommand(
                            TENANT_ID,
                            "1002",
                            "7001",
                            "APPROVE",
                            "同意上线",
                            evidence("3001").digest()
                    )
            ));
            Future<Object> reject = executor.submit(() -> decide(
                    service,
                    new DecideApprovalTaskCommand(
                            TENANT_ID,
                            "1003",
                            "7001",
                            "REJECT",
                            "风险未关闭",
                            evidence("3001").digest()
                    )
            ));

            List<Object> outcomes = List.of(approve.get(), reject.get());

            assertThat(outcomes.stream().filter(ApplicationException.class::isInstance)).hasSize(1);
            ApplicationException conflict = outcomes.stream()
                    .filter(ApplicationException.class::isInstance)
                    .map(ApplicationException.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertThat(conflict.errorCode()).isEqualTo(ApprovalErrorCodes.APPROVAL_TASK_DECISION_CONFLICT);
            assertThat(repository.records).hasSize(1);
            assertThat(events.events).hasSize(1);
            assertThat(repository.casWins.get()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private static Object decide(ApprovalTaskApplicationService service, DecideApprovalTaskCommand command) {
        try {
            return service.decide(command);
        } catch (ApplicationException exception) {
            return exception;
        }
    }

    private static ApprovalTask pendingTask() {
        return new ApprovalTask(
                TENANT_ID,
                "7001",
                "REQ-7001",
                "model_config_change",
                "model-hub",
                "model_provider",
                "3001",
                "创建模型供应商",
                ApprovalRiskLevel.HIGH_RISK,
                "1001",
                null,
                ApprovalStatus.PENDING,
                NOW.plusSeconds(3600),
                null,
                "trace-approval-7001",
                evidence("3001"),
                targetDigest("3001"),
                NOW.minusSeconds(60),
                NOW.minusSeconds(60),
                0
        );
    }

    private static final class ConcurrentRepository implements ApprovalTaskRepository {
        private final ApprovalTask pending;
        private final CyclicBarrier bothLoaded = new CyclicBarrier(2);
        private final AtomicBoolean casWins = new AtomicBoolean();
        private final List<ApprovalDecisionRecord> records = new CopyOnWriteArrayList<>();

        private ConcurrentRepository(ApprovalTask pending) {
            this.pending = pending;
        }

        @Override
        public Optional<ApprovalTask> findById(TenantId tenantId, String taskId) {
            try {
                bothLoaded.await();
                return Optional.of(pending);
            } catch (Exception exception) {
                throw new IllegalStateException("并发测试同步失败", exception);
            }
        }

        @Override
        public ApprovalTaskPage findTasks(ApprovalTaskQuery query) {
            throw new UnsupportedOperationException("本测试只覆盖决策");
        }

        @Override
        public ApprovalTask saveDecision(ApprovalTask task) {
            if (!casWins.compareAndSet(false, true)) {
                throw new ApprovalTaskDecisionConflictException();
            }
            return task;
        }

        @Override
        public void recordDecision(ApprovalDecisionRecord record) {
            records.add(record);
        }
    }

    private static final class RecordingOutbox implements ApprovalDecisionEventPublisher {
        private final List<ApprovalDecisionRecordedEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(ApprovalDecisionRecordedEvent event) {
            events.add(event);
        }
    }
}

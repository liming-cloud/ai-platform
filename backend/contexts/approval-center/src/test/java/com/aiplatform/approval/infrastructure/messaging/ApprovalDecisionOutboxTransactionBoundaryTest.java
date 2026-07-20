package com.aiplatform.approval.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.approval.application.ApprovalTaskApplicationService;
import com.aiplatform.approval.domain.event.ApprovalDecisionRecordedEvent;
import com.aiplatform.approval.domain.port.ApprovalDecisionEventPublisher;
import com.aiplatform.approval.infrastructure.messaging.outbox.ApprovalDecisionOutboxRecord;
import com.aiplatform.approval.infrastructure.messaging.publisher.RocketmqApprovalDecisionPublisher;
import com.aiplatform.approval.infrastructure.persistence.mapper.ApprovalDecisionOutboxMapper;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.infra.rocketmq.publisher.DomainEventPublisher;
import com.aiplatform.shared.kernel.TenantId;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitConfig(ApprovalDecisionOutboxTransactionBoundaryTest.TestConfiguration.class)
class ApprovalDecisionOutboxTransactionBoundaryTest {
    @jakarta.annotation.Resource
    private TransactionalDecisionUseCase useCase;

    @jakarta.annotation.Resource
    private ApprovalDecisionEventPublisher publisher;

    @jakarta.annotation.Resource
    private TransactionalOutboxMapper outbox;

    @jakarta.annotation.Resource
    private RecordingTransactionManager transactionManager;

    @BeforeEach
    void reset() {
        outbox.reset();
        transactionManager.reset();
    }

    @Test
    void decisionEventBecomesDispatchableOnlyAfterTheBusinessTransactionCommits() {
        useCase.publish(event(), false);

        assertThat(outbox.transactionActiveAtEnqueue).isTrue();
        assertThat(outbox.committed).isNotNull();
        assertThat(transactionManager.commitCount).isOne();
        assertThat(transactionManager.rollbackCount).isZero();
    }

    @Test
    void businessFailureAfterEnqueueRollsBackWithoutAnyBrokerCall() {
        assertThatThrownBy(() -> useCase.publish(event(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated commit blocker");

        assertThat(outbox.committed).isNull();
        assertThat(transactionManager.commitCount).isZero();
        assertThat(transactionManager.rollbackCount).isOne();
        assertThat(Arrays.stream(RocketmqApprovalDecisionPublisher.class.getDeclaredFields())
                .map(Field::getType))
                .noneMatch(DomainEventPublisher.class::isAssignableFrom);
    }

    @Test
    void outboxPublisherCannotBeUsedOutsideAnExistingBusinessTransaction() {
        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void approvalDecisionUseCaseDeclaresTheTransactionBoundary() throws Exception {
        assertThat(ApprovalTaskApplicationService.class.getMethod(
                "decide",
                com.aiplatform.approval.application.command.DecideApprovalTaskCommand.class
        ).getAnnotation(Transactional.class)).isNotNull();
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        TransactionalOutboxMapper approvalDecisionOutboxMapper() {
            return new TransactionalOutboxMapper();
        }

        @Bean
        ApprovalDecisionEventPublisher approvalDecisionEventPublisher(ApprovalDecisionOutboxMapper outbox) {
            return new RocketmqApprovalDecisionPublisher(outbox, new JsonMapper());
        }

        @Bean
        TransactionalDecisionUseCase transactionalDecisionUseCase(ApprovalDecisionEventPublisher publisher) {
            return new TransactionalDecisionUseCase(publisher);
        }
    }

    public static class TransactionalDecisionUseCase {
        private final ApprovalDecisionEventPublisher publisher;

        public TransactionalDecisionUseCase(ApprovalDecisionEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Transactional
        public void publish(ApprovalDecisionRecordedEvent event, boolean failAfterEnqueue) {
            publisher.publish(event);
            if (failAfterEnqueue) {
                throw new IllegalStateException("simulated commit blocker");
            }
        }
    }

    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private int commitCount;
        private int rollbackCount;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // AbstractPlatformTransactionManager 在 begin 后建立同步上下文。
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount++;
        }

        void reset() {
            commitCount = 0;
            rollbackCount = 0;
        }
    }

    static final class TransactionalOutboxMapper implements ApprovalDecisionOutboxMapper {
        private DomainEventMessage committed;
        private boolean transactionActiveAtEnqueue;

        @Override
        public int enqueue(DomainEventMessage message) {
            transactionActiveAtEnqueue = TransactionSynchronizationManager.isActualTransactionActive();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committed = message;
                }
            });
            return 1;
        }

        @Override
        public List<ApprovalDecisionOutboxRecord> claimBatch(
                String workerId,
                Instant now,
                Instant leaseUntil,
                int limit
        ) {
            throw new UnsupportedOperationException("本测试只覆盖事务入队");
        }

        @Override
        public int markPublished(long id, String workerId, Instant now) {
            throw new UnsupportedOperationException("本测试只覆盖事务入队");
        }

        @Override
        public int markRetry(long id, String workerId, Instant now, Instant nextAttemptAt, String lastError) {
            throw new UnsupportedOperationException("本测试只覆盖事务入队");
        }

        void reset() {
            committed = null;
            transactionActiveAtEnqueue = false;
        }
    }

    private static ApprovalDecisionRecordedEvent event() {
        return new ApprovalDecisionRecordedEvent(
                TenantId.of("2001"),
                "7001",
                "REQ-7001",
                "model-hub",
                "model_provider",
                "3001",
                "APPROVED",
                "APPROVE",
                "1002",
                "同意上线",
                "trace-approval-7001",
                "model-approval-evidence/v1",
                "a".repeat(64),
                "b".repeat(64),
                Instant.parse("2026-07-18T08:00:00Z")
        );
    }
}

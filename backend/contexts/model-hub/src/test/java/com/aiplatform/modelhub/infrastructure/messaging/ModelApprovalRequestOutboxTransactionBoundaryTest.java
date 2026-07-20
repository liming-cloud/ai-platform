package com.aiplatform.modelhub.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.infra.rocketmq.publisher.DomainEventPublisher;
import com.aiplatform.modelhub.ModelApprovalTestFixtures;
import com.aiplatform.modelhub.domain.ModelConfigChange;
import com.aiplatform.modelhub.domain.port.ModelConfigApprovalPort;
import com.aiplatform.modelhub.infrastructure.messaging.outbox.ModelApprovalRequestOutboxRecord;
import com.aiplatform.modelhub.infrastructure.messaging.publisher.RocketmqModelConfigApprovalPublisher;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelApprovalRequestOutboxMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitConfig(ModelApprovalRequestOutboxTransactionBoundaryTest.TestConfiguration.class)
class ModelApprovalRequestOutboxTransactionBoundaryTest {
    @jakarta.annotation.Resource
    private ModelConfigApprovalPort publisher;

    @jakarta.annotation.Resource
    private ApprovalRequestTxFacade facade;

    @jakarta.annotation.Resource
    private TransactionalApprovalOutboxMapper outbox;

    @jakarta.annotation.Resource
    private RecordingTransactionManager transactionManager;

    @jakarta.annotation.Resource
    private DomainEventPublisher broker;

    @BeforeEach
    void reset() {
        outbox.reset();
        transactionManager.reset();
    }

    @Test
    void publisherCannotEscapeTheOwningBusinessTransaction() {
        assertThatThrownBy(() -> publisher.requestApproval(change()))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(outbox.attempted).isFalse();
        verifyNoInteractions(broker);
    }

    @Test
    void approvalRequestBecomesDispatchableOnlyAfterCommit() {
        assertThat(facade.request(change(), false)).isEqualTo("REQ-91");

        assertThat(outbox.transactionActiveAtEnqueue).isTrue();
        assertThat(outbox.committed).isNotNull();
        assertThat(transactionManager.commitCount).isOne();
        assertThat(transactionManager.rollbackCount).isZero();
        verifyNoInteractions(broker);
    }

    @Test
    void laterBusinessFailureRollsBackApprovalRequestWithoutBrokerSideEffects() {
        assertThatThrownBy(() -> facade.request(change(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced business rollback");

        assertThat(outbox.transactionActiveAtEnqueue).isTrue();
        assertThat(outbox.committed).isNull();
        assertThat(transactionManager.commitCount).isZero();
        assertThat(transactionManager.rollbackCount).isOne();
        verifyNoInteractions(broker);
    }

    @Test
    void enqueueFailureRollsBackInsteadOfFallingBackToDirectMq() {
        outbox.fail = true;

        assertThatThrownBy(() -> facade.request(change(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox persistence failed");

        assertThat(outbox.transactionActiveAtEnqueue).isTrue();
        assertThat(outbox.committed).isNull();
        assertThat(transactionManager.commitCount).isZero();
        assertThat(transactionManager.rollbackCount).isOne();
        verifyNoInteractions(broker);
    }

    private static ModelConfigChange change() {
        return ModelApprovalTestFixtures.configChange(
                "2001", "REQ-91", "3001", "4001", "1001", "trace-approval-request-91",
                Instant.parse("2026-07-20T07:59:00Z")
        );
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        TransactionalApprovalOutboxMapper modelApprovalRequestOutboxMapper() {
            return new TransactionalApprovalOutboxMapper();
        }

        @Bean
        ModelConfigApprovalPort modelConfigApprovalPort(ModelApprovalRequestOutboxMapper outbox) {
            return new RocketmqModelConfigApprovalPublisher(outbox, new JsonMapper());
        }

        @Bean
        ApprovalRequestTxFacade approvalRequestTxFacade(ModelConfigApprovalPort publisher) {
            return new ApprovalRequestTxFacade(publisher);
        }

        @Bean
        DomainEventPublisher broker() {
            return mock(DomainEventPublisher.class);
        }
    }

    static class ApprovalRequestTxFacade {
        private final ModelConfigApprovalPort publisher;

        protected ApprovalRequestTxFacade(ModelConfigApprovalPort publisher) {
            this.publisher = publisher;
        }

        @Transactional
        public String request(ModelConfigChange change, boolean failAfterEnqueue) {
            String requestId = publisher.requestApproval(change);
            if (failAfterEnqueue) {
                throw new IllegalStateException("forced business rollback");
            }
            return requestId;
        }
    }

    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
        private int commitCount;
        private int rollbackCount;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return active.get();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            active.set(true);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount++;
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            // The outer interceptor receives the same exception and performs the rollback.
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            active.remove();
        }

        void reset() {
            commitCount = 0;
            rollbackCount = 0;
            active.remove();
        }
    }

    static final class TransactionalApprovalOutboxMapper implements ModelApprovalRequestOutboxMapper {
        private DomainEventMessage committed;
        private boolean transactionActiveAtEnqueue;
        private boolean attempted;
        private boolean fail;

        @Override
        public int enqueue(DomainEventMessage message) {
            attempted = true;
            transactionActiveAtEnqueue = TransactionSynchronizationManager.isActualTransactionActive();
            if (fail) {
                throw new IllegalStateException("outbox persistence failed");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committed = message;
                }
            });
            return 1;
        }

        @Override
        public List<ModelApprovalRequestOutboxRecord> claimBatch(
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
            attempted = false;
            fail = false;
        }
    }
}

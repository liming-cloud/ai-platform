package com.aiplatform.modelhub.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.modelhub.application.ModelApprovalDecisionApplicationService;
import com.aiplatform.modelhub.application.ModelConfigApplicationService;
import com.aiplatform.modelhub.application.ModelProviderApplicationService;
import com.aiplatform.modelhub.application.command.CreateModelConfigCommand;
import com.aiplatform.modelhub.application.command.DisableModelConfigCommand;
import com.aiplatform.modelhub.application.command.DisableModelProviderCommand;
import com.aiplatform.modelhub.application.command.UpdateModelConfigCommand;
import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.domain.ModelType;
import com.aiplatform.modelhub.domain.port.ModelUsagePolicyPublisher;
import com.aiplatform.modelhub.infrastructure.messaging.outbox.ModelUsagePolicyOutboxRecord;
import com.aiplatform.modelhub.infrastructure.messaging.publisher.RocketmqModelUsagePolicyPublisher;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelUsagePolicyOutboxMapper;
import com.aiplatform.modelhub.infrastructure.springai.adapter.ModelRuntimeAuthorityDigest;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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

@SpringJUnitConfig(ModelUsagePolicyOutboxTransactionBoundaryTest.TestConfiguration.class)
class ModelUsagePolicyOutboxTransactionBoundaryTest {
    @jakarta.annotation.Resource
    private ModelUsagePolicyPublisher publisher;

    @jakarta.annotation.Resource
    private TransactionalOutboxMapper outbox;

    @jakarta.annotation.Resource
    private RecordingTransactionManager transactionManager;

    @BeforeEach
    void reset() {
        outbox.reset();
        transactionManager.reset();
        TraceContext.ensureTraceId("trace-outbox-transaction");
    }

    @AfterEach
    void cleanupTrace() {
        TraceContext.clear();
    }

    @Test
    void enqueueCommitsOnlyAfterOneActiveTransactionCompletes() {
        publisher.publish(provider(), config());

        assertThat(outbox.transactionActiveAtEnqueue).isTrue();
        assertThat(outbox.committed).isNotNull();
        assertThat(transactionManager.commitCount).isOne();
        assertThat(transactionManager.rollbackCount).isZero();
    }

    @Test
    void enqueueFailureRollsBackInsteadOfFallingBackToDirectMq() {
        outbox.fail = true;

        assertThatThrownBy(() -> publisher.publish(provider(), config()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox persistence failed");

        assertThat(outbox.transactionActiveAtEnqueue).isTrue();
        assertThat(outbox.committed).isNull();
        assertThat(transactionManager.commitCount).isZero();
        assertThat(transactionManager.rollbackCount).isOne();
    }

    @Test
    void everyBusinessMutationThatEmitsPolicyIsTransactional() throws Exception {
        assertTransactional(ModelConfigApplicationService.class, "createModelConfig", CreateModelConfigCommand.class);
        assertTransactional(ModelConfigApplicationService.class, "updateModelConfig", UpdateModelConfigCommand.class);
        assertTransactional(ModelConfigApplicationService.class, "disableModelConfig", DisableModelConfigCommand.class);
        assertTransactional(ModelProviderApplicationService.class, "disableProvider", DisableModelProviderCommand.class);
        assertNotTransactional(
                ModelConfigApplicationService.class,
                "testConnectivity",
                TenantId.class,
                String.class,
                String.class,
                int.class
        );
        assertTransactional(
                ModelApprovalDecisionApplicationService.class,
                "applyProviderDecision",
                TenantId.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                boolean.class,
                Instant.class
        );
        assertTransactional(
                ModelApprovalDecisionApplicationService.class,
                "applyModelConfigDecision",
                TenantId.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                boolean.class,
                Instant.class
        );
    }

    private static void assertTransactional(Class<?> type, String method, Class<?>... parameters) throws Exception {
        assertThat(type.getMethod(method, parameters).getAnnotation(Transactional.class))
                .as(type.getSimpleName() + "." + method)
                .isNotNull();
    }

    private static void assertNotTransactional(Class<?> type, String method, Class<?>... parameters) throws Exception {
        assertThat(type.getMethod(method, parameters).getAnnotation(Transactional.class))
                .as(type.getSimpleName() + "." + method + " must not hold a transaction across the outbound probe")
                .isNull();
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        TransactionalOutboxMapper modelUsagePolicyOutboxMapper() {
            return new TransactionalOutboxMapper();
        }

        @Bean
        ModelUsagePolicyPublisher modelUsagePolicyPublisher(
                ModelUsagePolicyOutboxMapper outbox,
                ModelRuntimeAuthorityDigest authorityDigest
        ) {
            return new RocketmqModelUsagePolicyPublisher(outbox, new JsonMapper(), authorityDigest);
        }

        @Bean
        ModelRuntimeAuthorityDigest modelRuntimeAuthorityDigest() {
            return new ModelRuntimeAuthorityDigest(json -> "a".repeat(64), new JsonMapper());
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

    static final class TransactionalOutboxMapper implements ModelUsagePolicyOutboxMapper {
        private DomainEventMessage committed;
        private boolean transactionActiveAtEnqueue;
        private boolean fail;

        @Override
        public Long nextPolicyRevision() {
            return 1L;
        }

        @Override
        public String canonicalDigest(String payloadJson) {
            return "c".repeat(64);
        }

        @Override
        public int enqueue(DomainEventMessage message) {
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
        public List<ModelUsagePolicyOutboxRecord> claimBatch(
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
            fail = false;
        }
    }

    private static ModelProvider provider() {
        return new ModelProvider(
                TenantId.of("2001"),
                "3001",
                "silicon-flow",
                "硅基流动",
                "https://api.siliconflow.cn/v1",
                "api_key",
                new ModelSecretRef("9001", "sk-***"),
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                false,
                Instant.parse("2026-07-17T07:00:00Z"),
                Instant.parse("2026-07-17T07:00:00Z")
        );
    }

    private static ModelConfig config() {
        return new ModelConfig(
                TenantId.of("2001"),
                "4001",
                "3001",
                "BAAI/bge-m3",
                "BGE M3",
                ModelType.EMBEDDING,
                8192,
                1536,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of("providerKind", "openai-compatible", "dimensions", 1536)),
                ModelConnectivityStatus.PASSED,
                Instant.parse("2026-07-17T07:30:00Z"),
                "连接成功",
                Instant.parse("2026-07-17T07:00:00Z"),
                Instant.parse("2026-07-17T08:00:00Z")
        );
    }
}

package com.aiplatform.appstudio.presentation.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.aiplatform.appstudio.domain.modelpolicy.AppModelUsagePolicyProjection;
import com.aiplatform.appstudio.domain.port.modelpolicy.AppModelUsagePolicyProjectionRepository;
import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyEventReceiptRepository;
import com.aiplatform.appstudio.infrastructure.messaging.converter.ModelUsagePolicyPublishedMessageConverter;
import com.aiplatform.appstudio.infrastructure.messaging.integrity.ModelUsagePolicyPayloadIntegrityVerifier;
import com.aiplatform.appstudio.presentation.messaging.consumer.AppStudioModelUsagePolicyPublishedConsumer;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringJUnitConfig(AppStudioModelUsagePolicyConsumerTransactionContractTest.TestConfiguration.class)
class AppStudioModelUsagePolicyConsumerTransactionContractTest {
    @jakarta.annotation.Resource
    private AppStudioModelUsagePolicyPublishedConsumer consumer;

    @jakarta.annotation.Resource
    private TransactionalReceipts receipts;

    @jakarta.annotation.Resource
    private TransactionalProjectionRepository projections;

    @jakarta.annotation.Resource
    private RecordingTransactionManager transactionManager;

    @BeforeEach
    void reset() {
        receipts.reset();
        projections.reset();
        transactionManager.reset();
    }

    @Test
    void receiptAndProjectionCommitTogetherInOneActiveTransaction() {
        consumer.onMessage(message());

        assertThat(receipts.transactionActiveAtRecord).isTrue();
        assertThat(projections.transactionActiveAtSave).isTrue();
        assertThat(receipts.committedEventIds).containsExactly("event-transaction-1");
        assertThat(projections.committed).isNotNull();
        assertThat(transactionManager.commitCount).isOne();
        assertThat(transactionManager.rollbackCount).isZero();
    }

    @Test
    void projectionFailureRollsBackTheReceiptSoBrokerRetryCanConsumeAgain() {
        projections.fail = true;

        assertThatThrownBy(() -> consumer.onMessage(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("projection persistence failed");

        assertThat(receipts.transactionActiveAtRecord).isTrue();
        assertThat(projections.transactionActiveAtSave).isTrue();
        assertThat(receipts.committedEventIds).isEmpty();
        assertThat(projections.committed).isNull();
        assertThat(transactionManager.commitCount).isZero();
        assertThat(transactionManager.rollbackCount).isOne();
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        TransactionalReceipts receipts() {
            return new TransactionalReceipts();
        }

        @Bean
        TransactionalProjectionRepository projections() {
            return new TransactionalProjectionRepository();
        }

        @Bean
        AppStudioModelUsagePolicyPublishedConsumer consumer(
                AppModelUsagePolicyProjectionRepository projections,
                ModelUsagePolicyEventReceiptRepository receipts
        ) {
            return new AppStudioModelUsagePolicyPublishedConsumer(
                    projections,
                    receipts,
                    new ModelUsagePolicyPublishedMessageConverter(new JsonMapper()),
                    mock(ModelUsagePolicyPayloadIntegrityVerifier.class)
            );
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

    static final class TransactionalReceipts implements ModelUsagePolicyEventReceiptRepository {
        private final Set<String> committedEventIds = new LinkedHashSet<>();
        private boolean transactionActiveAtRecord;

        @Override
        public boolean recordIfAbsent(String eventId, long eventVersion, Instant occurredAt) {
            transactionActiveAtRecord = TransactionSynchronizationManager.isActualTransactionActive();
            if (committedEventIds.contains(eventId)) {
                return false;
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committedEventIds.add(eventId);
                }
            });
            return true;
        }

        void reset() {
            committedEventIds.clear();
            transactionActiveAtRecord = false;
        }
    }

    static final class TransactionalProjectionRepository implements AppModelUsagePolicyProjectionRepository {
        private AppModelUsagePolicyProjection committed;
        private boolean transactionActiveAtSave;
        private boolean fail;

        @Override
        public Optional<AppModelUsagePolicyProjection> findByModelConfigId(TenantId tenantId, String modelConfigId) {
            return Optional.ofNullable(committed);
        }

        @Override
        public AppModelUsagePolicyProjection save(AppModelUsagePolicyProjection projection) {
            transactionActiveAtSave = TransactionSynchronizationManager.isActualTransactionActive();
            if (fail) {
                throw new IllegalStateException("projection persistence failed");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committed = projection;
                }
            });
            return projection;
        }

        void reset() {
            committed = null;
            transactionActiveAtSave = false;
            fail = false;
        }
    }

    private static DomainEventMessage message() {
        return new DomainEventMessage(
                "event-transaction-1",
                "ModelUsagePolicyPublished",
                "2001",
                "ModelConfig",
                "4001",
                2,
                "schema/model-usage-policy.v2",
                Instant.parse("2026-07-17T08:00:00Z"),
                "trace-transaction-1",
                "model-hub",
                """
                        {
                          "modelConfigId": "4001",
                          "providerId": "3001",
                          "providerCode": "silicon-flow",
                          "providerKind": "openai-compatible",
                          "providerEndpoint": "https://api.siliconflow.cn/v1",
                          "authType": "api_key",
                          "secretRefId": "9001",
                          "modelCode": "deepseek-chat",
                          "modelConfigVersion": 7,
                          "modelType": "CHAT",
                          "capabilities": ["CHAT"],
                          "allowedUseCases": ["RAG_QUERY"],
                          "allowedApplicationIds": [],
                          "effectiveOptions": {"temperature": 0.2},
                          "effectiveOptionsDigest": "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                          "maxInputTokens": 8192,
                          "maxOutputTokens": 2048,
                          "policyVersion": "policy-1",
                          "policyRevision": 1,
                          "policyDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                          "enabled": true
                        }
                        """
        );
    }
}

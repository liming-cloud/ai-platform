package com.aiplatform.knowledge.presentation.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.knowledge.domain.modelpolicy.KnowledgeModelUsagePolicyProjection;
import com.aiplatform.knowledge.domain.port.modelpolicy.KnowledgeModelUsagePolicyEventReceiptRepository;
import com.aiplatform.knowledge.domain.port.modelpolicy.KnowledgeModelUsagePolicyProjectionRepository;
import com.aiplatform.knowledge.infrastructure.messaging.converter.ModelUsagePolicyPublishedMessageConverter;
import com.aiplatform.knowledge.infrastructure.messaging.integrity.ModelUsagePolicyPayloadIntegrityVerifier;
import com.aiplatform.knowledge.presentation.messaging.consumer.ModelUsagePolicyPublishedConsumer;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringJUnitConfig(ModelUsagePolicyConsumerTransactionContractTest.TestConfiguration.class)
class ModelUsagePolicyConsumerTransactionContractTest {
    @jakarta.annotation.Resource
    private ModelUsagePolicyPublishedConsumer consumer;

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
    void consumerDeclaresTheReceiptAndProjectionBoundaryAtClassLevel() {
        assertThat(ModelUsagePolicyPublishedConsumer.class.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void receiptAndProjectionCommitTogetherInOneActiveTransaction() {
        consumer.onMessage(message());

        assertThat(receipts.transactionActiveAtRecord).isTrue();
        assertThat(projections.transactionActiveAtSave).isTrue();
        assertThat(receipts.committedEventIds).containsExactly("event-kb-transaction-1");
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
        ModelUsagePolicyPublishedConsumer consumer(
                KnowledgeModelUsagePolicyProjectionRepository projections,
                KnowledgeModelUsagePolicyEventReceiptRepository receipts
        ) {
            return new ModelUsagePolicyPublishedConsumer(
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

    static final class TransactionalReceipts implements KnowledgeModelUsagePolicyEventReceiptRepository {
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

    static final class TransactionalProjectionRepository
            implements KnowledgeModelUsagePolicyProjectionRepository {
        private KnowledgeModelUsagePolicyProjection committed;
        private boolean transactionActiveAtSave;
        private boolean fail;

        @Override
        public Optional<KnowledgeModelUsagePolicyProjection> findByModelConfigId(
                TenantId tenantId,
                String modelConfigId
        ) {
            return Optional.ofNullable(committed);
        }

        @Override
        public KnowledgeModelUsagePolicyProjection save(KnowledgeModelUsagePolicyProjection projection) {
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
                "event-kb-transaction-1",
                "ModelUsagePolicyPublished",
                "2001",
                "ModelConfig",
                "4001",
                2,
                "schema/model-usage-policy.v2",
                Instant.parse("2026-07-21T08:00:00Z"),
                "trace-kb-transaction-1",
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
                          "secretMaskedValue": "sk-***",
                          "modelCode": "BAAI/bge-m3",
                          "modelConfigVersion": 7,
                          "modelType": "EMBEDDING",
                          "capabilities": ["EMBEDDING"],
                          "allowedUseCases": ["RAG_INDEXING", "RAG_QUERY"],
                          "allowedKnowledgeBaseIds": [],
                          "effectiveOptions": {"dimensions": 1536},
                          "effectiveOptionsDigest": "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                          "maxInputTokens": 8192,
                          "maxOutputTokens": 0,
                          "policyVersion": "mdl-4001-r7",
                          "policyRevision": 7,
                          "policyDigest": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                          "enabled": true
                        }
                        """
        );
    }
}

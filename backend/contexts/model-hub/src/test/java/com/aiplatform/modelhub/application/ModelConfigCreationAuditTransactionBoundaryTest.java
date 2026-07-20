package com.aiplatform.modelhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.aiplatform.modelhub.application.command.CreateModelConfigCommand;
import com.aiplatform.modelhub.application.idempotency.ModelCreationCommandDigest;
import com.aiplatform.modelhub.application.idempotency.ModelCreationRequestClaim;
import com.aiplatform.modelhub.application.idempotency.ModelCreationRequestRepository;
import com.aiplatform.modelhub.application.idempotency.ModelCreationResourceType;
import com.aiplatform.modelhub.application.idempotency.ModelCreationResultSnapshot;
import com.aiplatform.modelhub.application.query.ModelConfigQueryRepository;
import com.aiplatform.modelhub.application.result.CreateModelConfigResult;
import com.aiplatform.modelhub.domain.ModelApprovalCancellation;
import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigChange;
import com.aiplatform.modelhub.domain.ModelConfigCreationService;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelRuntimeConnectivity;
import com.aiplatform.modelhub.domain.ModelRuntimeValidation;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.domain.port.ModelConfigApprovalPort;
import com.aiplatform.modelhub.domain.port.ModelConfigRepository;
import com.aiplatform.modelhub.domain.port.ModelProviderRepository;
import com.aiplatform.modelhub.domain.port.ModelRuntimeOptionsPort;
import com.aiplatform.modelhub.domain.port.ModelUsagePolicyPublisher;
import com.aiplatform.modelhub.domain.service.ModelConfigActivationPolicy;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.net.OutboundEndpointPolicy;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

@SpringJUnitConfig(ModelConfigCreationAuditTransactionBoundaryTest.TestConfiguration.class)
class ModelConfigCreationAuditTransactionBoundaryTest {
    private static final TenantId TENANT = TenantId.of("2001");

    @jakarta.annotation.Resource
    private ModelCreationApplicationService service;

    @jakarta.annotation.Resource
    private TransactionalCreationRequests requests;

    @jakarta.annotation.Resource
    private TransactionalConfigs configs;

    @jakarta.annotation.Resource
    private TransactionalApprovals approvals;

    @jakarta.annotation.Resource
    private TransactionalPolicies policies;

    @jakarta.annotation.Resource
    private TransactionalAudits audits;

    @jakarta.annotation.Resource
    private RecordingTransactionManager transactionManager;

    @BeforeEach
    void resetState() {
        requests.reset();
        configs.reset();
        approvals.reset();
        policies.reset();
        audits.reset();
        transactionManager.reset();
    }

    @Test
    void concurrentRetryReplaysOneCommittedResourceApprovalPolicyAuditAndSnapshot() throws Exception {
        audits.block = true;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CreateModelConfigResult> owner = executor.submit(() -> service.createModelConfig(command()));
            assertThat(audits.entered.await(2, TimeUnit.SECONDS)).isTrue();
            Future<CreateModelConfigResult> retry = executor.submit(() -> service.createModelConfig(command()));
            assertThat(requests.replayWaiting.await(2, TimeUnit.SECONDS)).isTrue();
            audits.release.countDown();

            CreateModelConfigResult created = owner.get(2, TimeUnit.SECONDS);
            assertThat(retry.get(2, TimeUnit.SECONDS)).isEqualTo(created);
            assertThat(configs.committed).isNotNull();
            assertThat(approvals.committedCount).isOne();
            assertThat(policies.committedCount).isOne();
            assertThat(audits.committedEvents).hasSize(1);
            assertThat(requests.committedResult).isEqualTo(ModelCreationResultSnapshot.from(created));
            assertThat(audits.committedEvents.getFirst().action()).isEqualTo("MODEL_CONFIG_CREATE");
            assertThat(audits.committedEvents.getFirst().approvalReferenceId())
                    .isEqualTo(created.approvalRequestId());
            assertThat(requests.transactionActiveAtClaim).isTrue();
            assertThat(requests.transactionActiveAtComplete).isTrue();
            assertThat(configs.transactionActiveAtInsert).isTrue();
            assertThat(approvals.transactionActiveAtRequest).isTrue();
            assertThat(policies.transactionActiveAtPublish).isTrue();
            assertThat(audits.transactionActiveAtPublish).isTrue();
        } finally {
            audits.release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void auditFailureRollsBackEveryCreationEffectAndSameRequestCanRetryThenReplay() {
        audits.fail = true;

        assertThatThrownBy(() -> service.createModelConfig(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("model config audit failed");

        assertThat(requests.committedResult).isNull();
        assertThat(requests.hasInFlight()).isFalse();
        assertThat(configs.committed).isNull();
        assertThat(approvals.committedCount).isZero();
        assertThat(policies.committedCount).isZero();
        assertThat(audits.committedEvents).isEmpty();
        assertThat(transactionManager.commitCount).isZero();
        assertThat(transactionManager.rollbackCount).isOne();

        audits.fail = false;
        CreateModelConfigResult created = service.createModelConfig(command());
        assertThat(service.createModelConfig(command())).isEqualTo(created);

        assertThat(configs.committed).isNotNull();
        assertThat(approvals.committedCount).isOne();
        assertThat(policies.committedCount).isOne();
        assertThat(audits.committedEvents).hasSize(1);
        assertThat(requests.committedResult).isEqualTo(ModelCreationResultSnapshot.from(created));
        assertThat(transactionManager.commitCount).isEqualTo(2);
        assertThat(transactionManager.rollbackCount).isOne();
    }

    private static CreateModelConfigCommand command() {
        return new CreateModelConfigCommand(
                TENANT,
                "1001",
                "model-create-audit-transaction-001",
                "9001",
                "gpt-audit-safe",
                "Audit Safe Model",
                "CHAT",
                32768,
                null,
                new BigDecimal("0.001"),
                new BigDecimal("0.002"),
                Map.of("providerKind", "openai-compatible", "temperature", new BigDecimal("0.2"))
        );
    }

    private static ModelProvider provider() {
        return new ModelProvider(
                TENANT,
                "9001",
                "openai-compatible",
                "Enterprise Gateway",
                "https://models.example.test/v1",
                "api_key",
                new ModelSecretRef("9901", "credential-hidden"),
                ModelProviderStatus.ACTIVE,
                "REQ-PROVIDER-9001",
                true,
                Instant.parse("2026-07-21T02:00:00Z"),
                Instant.parse("2026-07-21T02:00:00Z")
        );
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        TransactionalCreationRequests creationRequests() {
            return new TransactionalCreationRequests();
        }

        @Bean
        TransactionalConfigs modelConfigs() {
            return new TransactionalConfigs();
        }

        @Bean
        ModelProviderRepository modelProviders() {
            ModelProviderRepository providers = mock(ModelProviderRepository.class);
            org.mockito.Mockito.when(providers.findById(TENANT, "9001")).thenReturn(Optional.of(provider()));
            return providers;
        }

        @Bean
        TransactionalApprovals modelApprovals() {
            return new TransactionalApprovals();
        }

        @Bean
        TransactionalPolicies modelPolicies() {
            return new TransactionalPolicies();
        }

        @Bean
        TransactionalAudits modelAudits() {
            return new TransactionalAudits();
        }

        @Bean
        ModelRuntimeOptionsPort runtimeOptions() {
            return new ModelRuntimeOptionsPort() {
                @Override
                public ModelRuntimeValidation validate(ModelProvider provider, ModelConfig config) {
                    return ModelRuntimeValidation.passed();
                }

                @Override
                public ModelRuntimeConnectivity testConnectivity(ModelProvider provider, ModelConfig config) {
                    throw new UnsupportedOperationException("本测试不执行连通性探测");
                }
            };
        }

        @Bean
        ModelConfigApplicationService modelConfigApplicationService(
                ModelProviderRepository providers,
                TransactionalConfigs configs,
                ModelRuntimeOptionsPort runtimeOptions,
                TransactionalApprovals approvals,
                TransactionalPolicies policies,
                TransactionalAudits audits
        ) {
            return new ModelConfigApplicationService(
                    providers,
                    configs,
                    mock(ModelConfigQueryRepository.class),
                    runtimeOptions,
                    approvals,
                    new ModelConfigCreationService(),
                    new ModelConfigActivationPolicy(),
                    policies,
                    audits,
                    Clock.fixed(Instant.parse("2026-07-21T03:00:00Z"), ZoneOffset.UTC)
            );
        }

        @Bean
        ModelProviderApplicationService modelProviderApplicationService() {
            return mock(ModelProviderApplicationService.class);
        }

        @Bean
        OutboundEndpointPolicy outboundEndpointPolicy() {
            OutboundEndpointPolicy policy = mock(OutboundEndpointPolicy.class);
            org.mockito.Mockito.when(policy.validateAndNormalize(org.mockito.ArgumentMatchers.any()))
                    .thenAnswer(invocation -> URI.create(invocation.getArgument(0)));
            return policy;
        }

        @Bean
        ModelCreationApplicationService modelCreationApplicationService(
                ModelProviderApplicationService providerService,
                ModelConfigApplicationService configService,
                ModelCreationRequestRepository requests,
                OutboundEndpointPolicy endpointPolicy
        ) {
            return new ModelCreationApplicationService(
                    providerService,
                    configService,
                    requests,
                    new ModelProviderConnectionContract(endpointPolicy, false),
                    new ModelCreationCommandDigest()
            );
        }
    }

    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
        private int commitCount;
        private int rollbackCount;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected boolean isExistingTransaction(Object transaction) { return active.get(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { active.set(true); }
        @Override protected void doCommit(DefaultTransactionStatus status) { commitCount++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbackCount++; }
        @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) { }
        @Override protected void doCleanupAfterCompletion(Object transaction) { active.remove(); }

        void reset() {
            commitCount = 0;
            rollbackCount = 0;
            active.remove();
        }
    }

    static final class TransactionalCreationRequests implements ModelCreationRequestRepository {
        private PendingRequest inFlight;
        private String committedDigest;
        private ModelCreationResultSnapshot committedResult;
        private boolean transactionActiveAtClaim;
        private boolean transactionActiveAtComplete;
        private CountDownLatch replayWaiting = new CountDownLatch(1);

        @Override
        public synchronized ModelCreationRequestClaim claim(
                TenantId tenantId,
                ModelCreationResourceType resourceType,
                String requestId,
                String requestDigest
        ) {
            transactionActiveAtClaim = TransactionSynchronizationManager.isActualTransactionActive();
            while (inFlight != null) {
                replayWaiting.countDown();
                try {
                    wait(2_000L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("等待模型创建事务时中断", exception);
                }
                if (inFlight != null) {
                    throw new IllegalStateException("等待模型创建事务完成超时");
                }
            }
            if (committedResult != null) {
                return ModelCreationRequestClaim.replay(committedDigest, committedResult);
            }
            PendingRequest pending = new PendingRequest(requestDigest);
            inFlight = pending;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (pending.result == null) {
                        throw new IllegalStateException("模型创建事务不得提交空快照");
                    }
                    committedDigest = pending.digest;
                    committedResult = pending.result;
                }

                @Override
                public void afterCompletion(int status) {
                    synchronized (TransactionalCreationRequests.this) {
                        if (inFlight == pending) {
                            inFlight = null;
                            TransactionalCreationRequests.this.notifyAll();
                        }
                    }
                }
            });
            return ModelCreationRequestClaim.acquired(requestDigest);
        }

        @Override
        public synchronized void complete(
                TenantId tenantId,
                ModelCreationResourceType resourceType,
                String requestId,
                String requestDigest,
                ModelCreationResultSnapshot result
        ) {
            transactionActiveAtComplete = TransactionSynchronizationManager.isActualTransactionActive();
            if (inFlight == null || !inFlight.digest.equals(requestDigest) || inFlight.result != null) {
                throw new IllegalStateException("模型创建快照完成栅栏失败");
            }
            inFlight.result = result;
        }

        synchronized boolean hasInFlight() { return inFlight != null; }

        synchronized void reset() {
            inFlight = null;
            committedDigest = null;
            committedResult = null;
            transactionActiveAtClaim = false;
            transactionActiveAtComplete = false;
            replayWaiting = new CountDownLatch(1);
        }
    }

    static final class PendingRequest {
        private final String digest;
        private ModelCreationResultSnapshot result;
        private PendingRequest(String digest) { this.digest = digest; }
    }

    static final class TransactionalConfigs implements ModelConfigRepository {
        private ModelConfig pending;
        private ModelConfig committed;
        private boolean transactionActiveAtInsert;

        @Override public String nextModelConfigId() { return "9101"; }

        @Override
        public synchronized ModelConfig insert(ModelConfig config) {
            transactionActiveAtInsert = TransactionSynchronizationManager.isActualTransactionActive();
            pending = config;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { committed = config; }
                @Override public void afterCompletion(int status) { pending = null; }
            });
            return config;
        }

        @Override public Optional<ModelConfig> findByProviderAndCode(TenantId tenantId, String providerId, String modelCode) {
            return committed != null && committed.modelCode().equals(modelCode)
                    ? Optional.of(committed) : Optional.empty();
        }

        @Override public Optional<ModelConfig> compareAndSet(ModelConfig value, int version, ModelConfigStatus status) { throw unsupported(); }
        @Override public Optional<ModelConfig> compareAndSetApprovalDecision(ModelConfig value, int version, ModelConfigStatus status, String requestId, String evidenceDigest, String targetDigest) { throw unsupported(); }
        @Override public Optional<ModelConfig> findById(TenantId tenantId, String providerId, String id) { return Optional.ofNullable(committed); }
        @Override public Optional<ModelConfig> findByModelConfigId(TenantId tenantId, String id) { return Optional.ofNullable(committed); }
        @Override public List<ModelConfig> findByProviderId(TenantId tenantId, String providerId) { return committed == null ? List.of() : List.of(committed); }

        synchronized void reset() {
            pending = null;
            committed = null;
            transactionActiveAtInsert = false;
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("本测试仅覆盖模型配置创建");
        }
    }

    static final class TransactionalApprovals implements ModelConfigApprovalPort {
        private int committedCount;
        private boolean transactionActiveAtRequest;

        @Override
        public String requestApproval(ModelConfigChange change) {
            transactionActiveAtRequest = TransactionSynchronizationManager.isActualTransactionActive();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { committedCount++; }
            });
            return change.changeId();
        }

        @Override public void cancelApproval(ModelApprovalCancellation cancellation) { throw new UnsupportedOperationException(); }
        void reset() { committedCount = 0; transactionActiveAtRequest = false; }
    }

    static final class TransactionalPolicies implements ModelUsagePolicyPublisher {
        private int committedCount;
        private boolean transactionActiveAtPublish;

        @Override
        public void publish(ModelProvider provider, ModelConfig config) {
            transactionActiveAtPublish = TransactionSynchronizationManager.isActualTransactionActive();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { committedCount++; }
            });
        }

        void reset() { committedCount = 0; transactionActiveAtPublish = false; }
    }

    static final class TransactionalAudits implements AuditEventPublisher {
        private final List<AuditEvent> committedEvents = new ArrayList<>();
        private boolean transactionActiveAtPublish;
        private boolean fail;
        private boolean block;
        private CountDownLatch entered = new CountDownLatch(1);
        private CountDownLatch release = new CountDownLatch(1);

        @Override
        public void publish(AuditEvent event) {
            transactionActiveAtPublish = TransactionSynchronizationManager.isActualTransactionActive();
            entered.countDown();
            if (block) {
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("等待并发重试超时");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("等待并发重试时中断", exception);
                }
            }
            if (fail) {
                throw new IllegalStateException("model config audit failed");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { committedEvents.add(event); }
            });
        }

        void reset() {
            committedEvents.clear();
            transactionActiveAtPublish = false;
            fail = false;
            block = false;
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }
    }
}

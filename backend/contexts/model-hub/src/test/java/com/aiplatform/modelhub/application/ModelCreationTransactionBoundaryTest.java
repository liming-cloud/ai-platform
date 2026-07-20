package com.aiplatform.modelhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.modelhub.application.command.CreateModelConfigCommand;
import com.aiplatform.modelhub.application.command.CreateModelProviderCommand;
import com.aiplatform.modelhub.application.idempotency.ModelCreationCommandDigest;
import com.aiplatform.modelhub.application.idempotency.ModelCreationRequestClaim;
import com.aiplatform.modelhub.application.idempotency.ModelCreationRequestRepository;
import com.aiplatform.modelhub.application.idempotency.ModelCreationResourceType;
import com.aiplatform.modelhub.application.idempotency.ModelCreationResultSnapshot;
import com.aiplatform.modelhub.application.result.CreateModelProviderResult;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.net.OutboundEndpointPolicy;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringJUnitConfig(ModelCreationTransactionBoundaryTest.TestConfiguration.class)
class ModelCreationTransactionBoundaryTest {
    private static final CreateModelProviderResult CREATED = new CreateModelProviderResult(
            "9001", "PENDING_APPROVAL", "REQ-9001"
    );

    @jakarta.annotation.Resource
    private ModelCreationApplicationService service;

    @jakarta.annotation.Resource
    private ModelProviderApplicationService providerService;

    private ModelProviderApplicationService providerCore;

    @jakarta.annotation.Resource
    private TransactionalCreationRequestRepository requests;

    @jakarta.annotation.Resource
    private RecordingTransactionManager transactionManager;

    @BeforeEach
    void resetState() {
        providerCore = AopTestUtils.getUltimateTargetObject(providerService);
        reset(providerCore);
        requests.reset();
        transactionManager.reset();
    }

    @Test
    void createSideEffectsAndExactResponseSnapshotCommitInOneOwningTransaction() {
        AtomicBoolean coreTransactionActive = new AtomicBoolean();
        when(providerCore.createProvider(any())).thenAnswer(invocation -> {
            coreTransactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return CREATED;
        });

        assertThat(service.createProvider(command())).isEqualTo(CREATED);

        assertThat(coreTransactionActive).isTrue();
        assertThat(requests.transactionActiveAtClaim).isTrue();
        assertThat(requests.transactionActiveAtComplete).isTrue();
        assertThat(requests.committedResult).isEqualTo(ModelCreationResultSnapshot.from(CREATED));
        assertThat(requests.incompleteCommit).isFalse();
        assertThat(transactionManager.commitCount).isOne();
        assertThat(transactionManager.rollbackCount).isZero();

        assertThat(service.createProvider(command())).isEqualTo(CREATED);
        verify(providerCore, times(1)).createProvider(any());
        assertThat(transactionManager.commitCount).isEqualTo(2);
    }

    @Test
    void failedCreateRollsBackItsClaimAndTheSameRequestCanRetryThenReplay() {
        AtomicInteger invocations = new AtomicInteger();
        when(providerCore.createProvider(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            if (invocations.getAndIncrement() == 0) {
                throw new IllegalStateException("forced creation failure");
            }
            return CREATED;
        });

        assertThatThrownBy(() -> service.createProvider(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced creation failure");
        assertThat(requests.committedResult).isNull();
        assertThat(requests.hasInFlightRequest()).isFalse();
        assertThat(transactionManager.commitCount).isZero();
        assertThat(transactionManager.rollbackCount).isOne();

        assertThat(service.createProvider(command())).isEqualTo(CREATED);
        assertThat(service.createProvider(command())).isEqualTo(CREATED);

        assertThat(requests.committedResult).isEqualTo(ModelCreationResultSnapshot.from(CREATED));
        assertThat(transactionManager.commitCount).isEqualTo(2);
        assertThat(transactionManager.rollbackCount).isOne();
        verify(providerCore, times(2)).createProvider(any());
    }

    @Test
    void bothPublicCreationSeamsDeclareTheOwningTransaction() throws Exception {
        assertTransactional("createProvider", CreateModelProviderCommand.class);
        assertTransactional("createModelConfig", CreateModelConfigCommand.class);
    }

    private static void assertTransactional(String method, Class<?> parameter) throws Exception {
        assertThat(ModelCreationApplicationService.class.getMethod(method, parameter)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    private static CreateModelProviderCommand command() {
        return new CreateModelProviderCommand(
                TenantId.of("2001"),
                "1001",
                "provider-create-transaction-001",
                "openai",
                "OpenAI 企业账号",
                "https://api.openai.example/v1",
                "api_key",
                "transaction-secret-123456"
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
        TransactionalCreationRequestRepository creationRequests() {
            return new TransactionalCreationRequestRepository();
        }

        @Bean
        ModelProviderApplicationService modelProviderApplicationService() {
            return mock(ModelProviderApplicationService.class);
        }

        @Bean
        ModelConfigApplicationService modelConfigApplicationService() {
            return mock(ModelConfigApplicationService.class);
        }

        @Bean
        OutboundEndpointPolicy outboundEndpointPolicy() {
            OutboundEndpointPolicy policy = mock(OutboundEndpointPolicy.class);
            when(policy.validateAndNormalize(any())).thenAnswer(invocation -> URI.create(invocation.getArgument(0)));
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
            // The outer interceptor receives the same failure and performs the rollback.
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

    static final class TransactionalCreationRequestRepository implements ModelCreationRequestRepository {
        private PendingRequest inFlight;
        private String committedDigest;
        private ModelCreationResultSnapshot committedResult;
        private boolean transactionActiveAtClaim;
        private boolean transactionActiveAtComplete;
        private boolean incompleteCommit;

        @Override
        public ModelCreationRequestClaim claim(
                TenantId tenantId,
                ModelCreationResourceType resourceType,
                String requestId,
                String requestDigest
        ) {
            transactionActiveAtClaim = TransactionSynchronizationManager.isActualTransactionActive();
            if (committedResult != null) {
                return ModelCreationRequestClaim.replay(committedDigest, committedResult);
            }
            if (inFlight != null) {
                throw new IllegalStateException("test repository permits only one in-flight transaction");
            }
            PendingRequest pending = new PendingRequest(requestDigest);
            inFlight = pending;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (pending.result == null) {
                        incompleteCommit = true;
                        return;
                    }
                    committedDigest = pending.digest;
                    committedResult = pending.result;
                }

                @Override
                public void afterCompletion(int status) {
                    if (inFlight == pending) {
                        inFlight = null;
                    }
                }
            });
            return ModelCreationRequestClaim.acquired(requestDigest);
        }

        @Override
        public void complete(
                TenantId tenantId,
                ModelCreationResourceType resourceType,
                String requestId,
                String requestDigest,
                ModelCreationResultSnapshot result
        ) {
            transactionActiveAtComplete = TransactionSynchronizationManager.isActualTransactionActive();
            if (inFlight == null || !inFlight.digest.equals(requestDigest) || inFlight.result != null) {
                throw new IllegalStateException("test completion fence failed");
            }
            inFlight.result = result;
        }

        boolean hasInFlightRequest() {
            return inFlight != null;
        }

        void reset() {
            inFlight = null;
            committedDigest = null;
            committedResult = null;
            transactionActiveAtClaim = false;
            transactionActiveAtComplete = false;
            incompleteCommit = false;
        }
    }

    static final class PendingRequest {
        private final String digest;
        private ModelCreationResultSnapshot result;

        private PendingRequest(String digest) {
            this.digest = digest;
        }
    }
}

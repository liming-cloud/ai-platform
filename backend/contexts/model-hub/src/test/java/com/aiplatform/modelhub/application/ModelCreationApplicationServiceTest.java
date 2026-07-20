package com.aiplatform.modelhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.modelhub.application.command.CreateModelConfigCommand;
import com.aiplatform.modelhub.application.command.CreateModelProviderCommand;
import com.aiplatform.modelhub.application.error.ModelHubErrorCodes;
import com.aiplatform.modelhub.application.idempotency.ModelCreationCommandDigest;
import com.aiplatform.modelhub.application.idempotency.ModelCreationRequestClaim;
import com.aiplatform.modelhub.application.idempotency.ModelCreationRequestRepository;
import com.aiplatform.modelhub.application.idempotency.ModelCreationResourceType;
import com.aiplatform.modelhub.application.idempotency.ModelCreationResultSnapshot;
import com.aiplatform.modelhub.application.result.CreateModelConfigResult;
import com.aiplatform.modelhub.application.result.CreateModelProviderResult;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.net.OutboundEndpointPolicy;
import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ModelCreationApplicationServiceTest {
    private static final TenantId TENANT = TenantId.of("2001");
    @Test
    void sameProviderRequestReplaysTheExactCommittedResponseWithoutAggregateLookupOrSecretPersistence() {
        BlockingRequestRepository requests = new BlockingRequestRepository();
        ModelProviderApplicationService providerService = mock(ModelProviderApplicationService.class);
        ModelConfigApplicationService configService = mock(ModelConfigApplicationService.class);
        CreateModelProviderResult created = new CreateModelProviderResult(
                "9001", "PENDING_APPROVAL", "REQ-9001"
        );
        when(providerService.createProvider(any())).thenReturn(created);
        ModelCreationApplicationService service = service(providerService, configService, requests);
        String credential = "arbitrary-runtime-credential-987654";
        CreateModelProviderCommand command = providerCommand(
                "provider-create-001", "OpenAI 企业账号", credential
        );

        CreateModelProviderResult first = service.createProvider(command);
        CreateModelProviderResult replay = service.createProvider(command);

        assertThat(first).isEqualTo(created);
        assertThat(replay).isEqualTo(created);
        verify(providerService, times(1)).createProvider(any());
        verifyNoInteractions(configService);
        assertThat(requests.onlyDigest())
                .matches("[0-9a-f]{64}")
                .doesNotContain(credential);
        assertThat(requests.onlyResult()).isEqualTo(ModelCreationResultSnapshot.from(created));
    }

    @Test
    void sameProviderRequestWithDifferentNormalizedCommandFailsClosedBeforeAnotherSideEffect() {
        BlockingRequestRepository requests = new BlockingRequestRepository();
        ModelProviderApplicationService providerService = mock(ModelProviderApplicationService.class);
        when(providerService.createProvider(any())).thenReturn(new CreateModelProviderResult(
                "9001", "PENDING_APPROVAL", "REQ-9001"
        ));
        ModelCreationApplicationService service = service(
                providerService, mock(ModelConfigApplicationService.class), requests
        );
        service.createProvider(providerCommand("provider-create-001", "OpenAI 企业账号", "secret-one-123456"));

        assertThatThrownBy(() -> service.createProvider(providerCommand(
                "provider-create-001", "OpenAI 另一账号", "secret-one-123456"
        )))
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ModelHubErrorCodes.CREATION_IDEMPOTENCY_CONFLICT));
        verify(providerService, times(1)).createProvider(any());
    }

    @Test
    void sameModelRequestReplaysTheExactCommittedResponseWithoutAggregateLookupOrRepeatedSideEffects() {
        BlockingRequestRepository requests = new BlockingRequestRepository();
        ModelProviderApplicationService providerService = mock(ModelProviderApplicationService.class);
        ModelConfigApplicationService configService = mock(ModelConfigApplicationService.class);
        CreateModelConfigResult created = new CreateModelConfigResult(
                "9001", "9101", "gpt-4.1-mini", "CHAT", "PENDING_APPROVAL", "REQ-9101", "NOT_TESTED"
        );
        when(configService.createModelConfig(any())).thenReturn(created);
        ModelCreationApplicationService service = service(providerService, configService, requests);
        CreateModelConfigCommand command = modelCommand("model-create-001");

        assertThat(service.createModelConfig(command)).isEqualTo(created);
        assertThat(service.createModelConfig(command)).isEqualTo(created);

        verify(configService, times(1)).createModelConfig(any());
        verifyNoInteractions(providerService);
        assertThat(requests.size()).isOne();
    }

    @Test
    void concurrentProviderRetriesHaveOneOwnerAndOneCommittedReplay() throws Exception {
        BlockingRequestRepository requests = new BlockingRequestRepository();
        ModelProviderApplicationService providerService = mock(ModelProviderApplicationService.class);
        AtomicInteger sideEffects = new AtomicInteger();
        CountDownLatch coreEntered = new CountDownLatch(1);
        CountDownLatch releaseCore = new CountDownLatch(1);
        when(providerService.createProvider(any())).thenAnswer(invocation -> {
            sideEffects.incrementAndGet();
            coreEntered.countDown();
            assertThat(releaseCore.await(2, TimeUnit.SECONDS)).isTrue();
            return new CreateModelProviderResult("9001", "PENDING_APPROVAL", "REQ-9001");
        });
        ModelCreationApplicationService service = service(
                providerService,
                mock(ModelConfigApplicationService.class),
                requests
        );
        CreateModelProviderCommand command = providerCommand(
                "provider-concurrent-001", "OpenAI 企业账号", "concurrent-secret-123456"
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CreateModelProviderResult> owner = executor.submit(() -> service.createProvider(command));
            assertThat(coreEntered.await(2, TimeUnit.SECONDS)).isTrue();
            Future<CreateModelProviderResult> retry = executor.submit(() -> service.createProvider(command));
            assertThat(requests.replayWaiting.await(2, TimeUnit.SECONDS)).isTrue();
            releaseCore.countDown();

            assertThat(owner.get(2, TimeUnit.SECONDS).providerId()).isEqualTo("9001");
            assertThat(retry.get(2, TimeUnit.SECONDS).providerId()).isEqualTo("9001");
            assertThat(sideEffects).hasValue(1);
            verify(providerService, times(1)).createProvider(any());
        } finally {
            releaseCore.countDown();
            executor.shutdownNow();
        }
    }

    private static ModelCreationApplicationService service(
            ModelProviderApplicationService providerService,
            ModelConfigApplicationService configService,
            ModelCreationRequestRepository requests
    ) {
        OutboundEndpointPolicy endpointPolicy = mock(OutboundEndpointPolicy.class);
        when(endpointPolicy.validateAndNormalize(any())).thenAnswer(invocation ->
                URI.create(invocation.getArgument(0))
        );
        return new ModelCreationApplicationService(
                providerService,
                configService,
                requests,
                new ModelProviderConnectionContract(endpointPolicy, false),
                new ModelCreationCommandDigest()
        );
    }

    private static CreateModelProviderCommand providerCommand(
            String requestId,
            String providerName,
            String credential
    ) {
        return new CreateModelProviderCommand(
                TENANT,
                "1001",
                requestId,
                "openai",
                providerName,
                "https://api.openai.example/v1",
                "api_key",
                credential
        );
    }

    private static CreateModelConfigCommand modelCommand(String requestId) {
        return new CreateModelConfigCommand(
                TENANT,
                "1001",
                requestId,
                "9001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                "CHAT",
                128000,
                null,
                new BigDecimal("0.0004"),
                new BigDecimal("0.0016"),
                Map.of("providerKind", "openai-compatible", "temperature", new BigDecimal("0.2"))
        );
    }

    private static final class BlockingRequestRepository implements ModelCreationRequestRepository {
        private final Map<Key, StoredRequest> requests = new HashMap<>();
        private final CountDownLatch replayWaiting = new CountDownLatch(1);

        @Override
        public synchronized ModelCreationRequestClaim claim(
                TenantId tenantId,
                ModelCreationResourceType resourceType,
                String requestId,
                String requestDigest
        ) {
            Key key = new Key(tenantId, resourceType, requestId);
            StoredRequest stored = requests.get(key);
            if (stored == null) {
                requests.put(key, new StoredRequest(requestDigest));
                return ModelCreationRequestClaim.acquired(requestDigest);
            }
            while (stored.result == null) {
                replayWaiting.countDown();
                try {
                    wait(2_000L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("等待模型创建请求完成时被中断", exception);
                }
                if (stored.result == null) {
                    throw new IllegalStateException("等待模型创建请求完成超时");
                }
            }
            return ModelCreationRequestClaim.replay(stored.digest, stored.result);
        }

        @Override
        public synchronized void complete(
                TenantId tenantId,
                ModelCreationResourceType resourceType,
                String requestId,
                String requestDigest,
                ModelCreationResultSnapshot result
        ) {
            StoredRequest stored = requests.get(new Key(tenantId, resourceType, requestId));
            if (stored == null || !stored.digest.equals(requestDigest) || stored.result != null) {
                throw new IllegalStateException("模型创建请求完成条件不满足");
            }
            stored.result = result;
            notifyAll();
        }

        synchronized String onlyDigest() {
            return requests.values().iterator().next().digest;
        }

        synchronized ModelCreationResultSnapshot onlyResult() {
            return requests.values().iterator().next().result;
        }

        synchronized int size() {
            return requests.size();
        }
    }

    private record Key(TenantId tenantId, ModelCreationResourceType resourceType, String requestId) {
    }

    private static final class StoredRequest {
        private final String digest;
        private ModelCreationResultSnapshot result;

        private StoredRequest(String digest) {
            this.digest = digest;
        }
    }
}

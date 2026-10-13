package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.CreatePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentCreationOutcome;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentPage;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.service.PlatformModelDeploymentVerificationResult;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformModelDeploymentServiceTest {

    @Test
    void 无平台模型权限时不得读取候选连接或写入部署() {
        var service = new PlatformModelDeploymentService(
                new ForbiddenDeploymentRepository(), new ForbiddenConnectionRepository(), new ForbiddenDiscoveryLookup(),
                new PlatformModelProbeProviderService(java.util.List.of(), Clock.systemUTC()),
                new ForbiddenVerificationStore(), Clock.systemUTC(), () -> 620000000000000001L, Duration.ofHours(24));

        assertThrows(ModelCatalogPermissionDeniedException.class, () -> service.create(new CreatePlatformModelDeploymentCommand(
                sessionWithoutPlatformModelWrite(), "platform-deploy-0001", "610000000000000001", 1,
                "deterministic-embedding", "平台嵌入", ModelType.EMBEDDING, Set.of(ModelCapability.EMBEDDING),
                8192, null, 1024)));
    }

    private static PlatformRequestContext sessionWithoutPlatformModelWrite() {
        return new PlatformRequestContext(new SubjectId(101L), CredentialType.PERSON, AccessChannel.CONSOLE, 1L,
                Set.of(PlatformPermissions.SESSION_ACCESS), "trc_platform_deployment");
    }

    private static final class ForbiddenDeploymentRepository implements PlatformModelDeploymentRepository {
        @Override public PlatformModelDeploymentCreationOutcome create(PlatformModelDeploymentCreation creation) { throw new AssertionError("无权请求不得写入平台部署"); }
        @Override public Optional<PlatformModelDeployment> findById(PlatformModelDeploymentId id) { throw new AssertionError("无权请求不得读取平台部署"); }
        @Override public PlatformModelDeploymentPage findPage(long pageNumber, long pageSize) { throw new AssertionError("无权请求不得读取平台部署"); }
        @Override public boolean update(PlatformModelDeployment deployment, long expectedVersion) { throw new AssertionError("无权请求不得更新平台部署"); }
    }
    private static final class ForbiddenConnectionRepository implements PlatformProviderConnectionRepository {
        @Override public com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreationOutcome create(com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation creation) { throw new AssertionError("无权请求不得写入平台连接"); }
        @Override public Optional<PlatformProviderConnection> findById(PlatformProviderConnectionId id) { throw new AssertionError("无权请求不得读取平台连接"); }
    }
    private static final class ForbiddenDiscoveryLookup implements PlatformProviderModelDiscoveryLookup {
        @Override public Optional<com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate> findCurrentCandidate(PlatformProviderConnectionId connectionId, long connectionRevision, String modelCode, ModelType modelType, java.time.Instant now) { throw new AssertionError("无权请求不得读取平台候选"); }
        @Override public com.aiplatform.foundation.shared.contract.paging.PageResult<com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate> findCurrentCandidates(PlatformProviderConnectionId connectionId, long connectionRevision, com.aiplatform.foundation.shared.contract.paging.PageQuery page, java.time.Instant now, String q) { throw new AssertionError("无权请求不得读取平台候选"); }
    }
    private static final class ForbiddenVerificationStore implements PlatformModelDeploymentVerificationStore {
        @Override public void commit(PlatformModelDeploymentVerificationResult verification, long expectedDeploymentVersion) { throw new AssertionError("无权请求不得写入平台验证事实"); }
    }
}

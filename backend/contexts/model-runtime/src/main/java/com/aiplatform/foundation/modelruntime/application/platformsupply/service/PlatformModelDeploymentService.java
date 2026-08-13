package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentAvailability;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentAvailabilityNextStep;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.CreatePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.DisablePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.ProbePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.PublishPlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.CreatePlatformModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformModelDeploymentProbeRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformModelDeploymentView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.ProbePlatformModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.application.platformsupply.query.ListPlatformModelDeploymentsQuery;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityObservation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelCapabilityVerificationId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.service.PlatformModelDeploymentVerification;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;

/** 编排平台模型部署，明确拒绝租户连接、过期发现和漂移的连接修订。 */
public final class PlatformModelDeploymentService {
    private final PlatformModelDeploymentRepository deployments;
    private final PlatformProviderConnectionRepository connections;
    private final PlatformProviderModelDiscoveryLookup discoveries;
    private final PlatformModelProbeProviderService probeProvider;
    private final PlatformModelDeploymentVerificationStore verificationStore;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final Duration verificationValidity;

    public PlatformModelDeploymentService(
            PlatformModelDeploymentRepository deployments, PlatformProviderConnectionRepository connections,
            PlatformProviderModelDiscoveryLookup discoveries, PlatformModelProbeProviderService probeProvider,
            PlatformModelDeploymentVerificationStore verificationStore, Clock clock,
            EntityIdGenerator identifiers, Duration verificationValidity) {
        this.deployments = Objects.requireNonNull(deployments, "平台模型部署仓储不能为空");
        this.connections = Objects.requireNonNull(connections, "平台供应商连接仓储不能为空");
        this.discoveries = Objects.requireNonNull(discoveries, "平台模型发现读取端口不能为空");
        this.probeProvider = Objects.requireNonNull(probeProvider, "平台模型探测用例不能为空");
        this.verificationStore = Objects.requireNonNull(verificationStore, "平台模型能力验证存储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.verificationValidity = requirePositive(verificationValidity);
    }

    public CreatePlatformModelDeploymentResult create(CreatePlatformModelDeploymentCommand command) {
        Objects.requireNonNull(command, "平台模型部署创建命令不能为空");
        requirePermission(command.context());
        var connection = loadVerifiedConnection(command.providerConnectionId(), command.expectedConnectionVersion());
        var candidate = discoveries.findCurrentCandidate(connection.id(), connection.version(), command.modelCode(),
                command.modelType(), clock.instant()).orElseThrow(() -> new IllegalStateException("平台模型发现候选不可用"));
        if (!candidate.capabilities().containsAll(command.capabilities())) {
            throw new IllegalStateException("平台模型发现候选不覆盖声明能力");
        }
        var deployment = PlatformModelDeployment.create(new PlatformModelDeploymentId(identifiers.nextId()), connection.id(),
                connection.version(), command.modelCode(), command.displayName(), command.modelType(), command.capabilities(),
                command.contextWindow(), command.outputLimit(), command.embeddingDimension(), clock.instant());
        var outcome = deployments.create(new PlatformModelDeploymentCreation(deployment, command.idempotencyKey(), fingerprint(command)));
        return new CreatePlatformModelDeploymentResult(toView(outcome.deployment(), connection, clock.instant()), outcome.replayed());
    }

    public PageResult<PlatformModelDeploymentView> list(ListPlatformModelDeploymentsQuery query) {
        Objects.requireNonNull(query, "平台模型部署列表查询不能为空");
        requirePermission(query.context());
        var page = deployments.findPage(query.page().pageNumber(), query.page().pageSize());
        return PageResult.of(page.items().stream().map(deployment -> toView(deployment,
                connections.findById(deployment.providerConnectionId()).orElse(null), clock.instant())).toList(),
                query.page(), page.totalElements());
    }

    public ProbePlatformModelDeploymentResult probe(ProbePlatformModelDeploymentCommand command) {
        Objects.requireNonNull(command, "平台模型部署探测命令不能为空");
        requirePermission(command.context());
        var deployment = load(command.modelDeploymentId(), command.expectedVersion());
        var connection = loadFrozenConnection(deployment);
        var result = probeProvider.probe(new PlatformModelDeploymentProbeRequest(connection.providerKind(), connection.endpoint(),
                connection.credentialReference(), deployment.modelCode(), deployment.capabilities(), command.timeout()));
        var verification = PlatformModelDeploymentVerification.verify(deployment,
                new PlatformModelCapabilityVerificationId(identifiers.nextId()),
                new ModelCapabilityObservation(result.reachable(), result.capabilities(), result.detailCode(),
                        result.elapsed(), result.probedAt()), result.probedAt().plus(verificationValidity));
        verificationStore.commit(verification, command.expectedVersion());
        return new ProbePlatformModelDeploymentResult(toView(verification.revisedDeployment(), connection, result.probedAt()),
                verification.verification().succeeded(), result.capabilities(), result.elapsed(),
                verification.verification().resultCode(), result.probedAt());
    }

    public PlatformModelDeploymentView publish(PublishPlatformModelDeploymentCommand command) {
        Objects.requireNonNull(command, "平台模型部署发布命令不能为空");
        requirePermission(command.context());
        var deployment = load(command.modelDeploymentId(), command.expectedVersion());
        var connection = loadFrozenConnection(deployment);
        var published = deployment.publish(clock.instant());
        if (!deployments.update(published, command.expectedVersion())) throw new IllegalStateException("平台模型部署修订已变化");
        return toView(published, connection, clock.instant());
    }

    /** 停用部署以阻断新目录发布和运行时调用，保留既有验证审计。 */
    public PlatformModelDeploymentView disable(DisablePlatformModelDeploymentCommand command) {
        Objects.requireNonNull(command, "平台模型部署停用命令不能为空");
        requirePermission(command.context());
        var deployment = load(command.modelDeploymentId(), command.expectedVersion());
        var disabled = deployment.disable();
        if (disabled != deployment && !deployments.update(disabled, command.expectedVersion())) {
            throw new IllegalStateException("平台模型部署修订已变化");
        }
        return toView(disabled, null, clock.instant());
    }

    private PlatformModelDeployment load(String id, long expectedVersion) {
        var deployment = deployments.findById(PlatformModelDeploymentId.parse(id))
                .orElseThrow(() -> new IllegalArgumentException("平台模型部署不存在"));
        if (deployment.version() != expectedVersion) throw new IllegalStateException("平台模型部署修订已变化");
        return deployment;
    }

    private PlatformProviderConnection loadVerifiedConnection(String id, long expectedRevision) {
        var connection = connections.findById(PlatformProviderConnectionId.parse(id))
                .orElseThrow(() -> new IllegalArgumentException("平台供应商连接不存在"));
        if (connection.version() != expectedRevision
                || connection.status() != com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus.VERIFIED) {
            throw new IllegalStateException("平台供应商连接不可用");
        }
        return connection;
    }

    private PlatformProviderConnection loadFrozenConnection(PlatformModelDeployment deployment) {
        return loadVerifiedConnection(deployment.providerConnectionId().toString(), deployment.providerConnectionRevision());
    }

    private static void requirePermission(PlatformRequestContext context) {
        context = Objects.requireNonNull(context, "平台请求上下文不能为空");
        if (!context.hasPermission(PlatformPermissions.MODEL_PLATFORM_WRITE)) throw new ModelCatalogPermissionDeniedException();
    }

    private static Duration requirePositive(Duration value) {
        value = Objects.requireNonNull(value, "平台模型能力验证有效期不能为空");
        if (value.isNegative() || value.isZero()) throw new IllegalArgumentException("平台模型能力验证有效期必须大于零");
        return value;
    }

    private static PlatformModelDeploymentView toView(PlatformModelDeployment deployment,
            PlatformProviderConnection connection, java.time.Instant now) {
        var availability = availability(deployment, connection, now);
        return new PlatformModelDeploymentView(deployment.id().toString(), deployment.providerConnectionId().toString(),
                deployment.providerConnectionRevision(), deployment.modelCode(), deployment.displayName(), deployment.modelType().name(),
                deployment.capabilities(), deployment.contextWindow(), deployment.outputLimit(), deployment.embeddingDimension(),
                deployment.verificationExpiresAt(), deployment.status().name(), availability.status(), availability.nextStep(),
                deployment.version(), deployment.createdAt());
    }

    private static Availability availability(PlatformModelDeployment deployment, PlatformProviderConnection connection,
            java.time.Instant now) {
        if (deployment.status() == PlatformModelDeploymentStatus.DISABLED) return new Availability(ModelDeploymentAvailability.UNAVAILABLE, ModelDeploymentAvailabilityNextStep.NONE);
        if (connection == null || connection.status() != com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus.VERIFIED
                || connection.version() != deployment.providerConnectionRevision()) return new Availability(ModelDeploymentAvailability.UNAVAILABLE, ModelDeploymentAvailabilityNextStep.VERIFY_CONNECTION);
        if (deployment.status() == PlatformModelDeploymentStatus.DRAFT) return new Availability(ModelDeploymentAvailability.UNAVAILABLE, ModelDeploymentAvailabilityNextStep.PROBE_MODEL);
        if (deployment.verificationExpiresAt() == null || !deployment.verificationExpiresAt().isAfter(now)) return new Availability(ModelDeploymentAvailability.DEGRADED, ModelDeploymentAvailabilityNextStep.REPROBE_MODEL);
        return deployment.status() == PlatformModelDeploymentStatus.VERIFIED
                ? new Availability(ModelDeploymentAvailability.HEALTHY, ModelDeploymentAvailabilityNextStep.PUBLISH)
                : new Availability(ModelDeploymentAvailability.HEALTHY, ModelDeploymentAvailabilityNextStep.NONE);
    }

    private static String fingerprint(CreatePlatformModelDeploymentCommand command) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var value : java.util.List.of(command.providerConnectionId(), command.modelCode(), command.displayName(), command.modelType().name(), Integer.toString(command.contextWindow()), Objects.toString(command.outputLimit(), ""), Objects.toString(command.embeddingDimension(), ""))) update(digest, value);
            command.capabilities().stream().sorted(Comparator.comparing(Enum::name)).forEach(capability -> update(digest, capability.name()));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception); }
    }
    private static void update(MessageDigest digest, String value) { digest.update(value.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0); }
    private record Availability(ModelDeploymentAvailability status, ModelDeploymentAvailabilityNextStep nextStep) {}
}

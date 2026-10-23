package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentAvailability;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentAvailabilityNextStep;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.CreatePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.DisablePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.ProbePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.PublishPlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.CreatePlatformModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformModelDeploymentView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.ProbePlatformModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.application.platformsupply.query.ListPlatformModelDeploymentsQuery;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionPublicationRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;

/** 编排平台模型部署，明确拒绝租户连接、过期发现和漂移的连接修订。 */
public final class PlatformModelDeploymentService {
    private final PlatformModelDeploymentRepository deployments;
    private final PlatformProviderConnectionRepository connections;
    private final PlatformProviderConnectionPublicationRepository publications;
    private final PlatformProviderModelDiscoveryLookup discoveries;
    private final PlatformModelProbeProviderService probeProvider;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final boolean writesAvailable;
    private final com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.publication.PlatformModelSupplyPublicationStore supply;

    public PlatformModelDeploymentService(
            PlatformModelDeploymentRepository deployments, PlatformProviderConnectionRepository connections,
            PlatformProviderConnectionPublicationRepository publications,
            PlatformProviderModelDiscoveryLookup discoveries, PlatformModelProbeProviderService probeProvider,
            Clock clock, EntityIdGenerator identifiers, boolean writesAvailable,
            com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.publication.PlatformModelSupplyPublicationStore supply) {
        this.deployments = Objects.requireNonNull(deployments, "平台模型部署仓储不能为空");
        this.connections = Objects.requireNonNull(connections, "平台供应商连接仓储不能为空");
        this.publications = Objects.requireNonNull(publications, "平台连接发布仓储不能为空");
        this.discoveries = Objects.requireNonNull(discoveries, "平台模型发现读取端口不能为空");
        this.probeProvider = Objects.requireNonNull(probeProvider, "平台模型探测用例不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.writesAvailable = writesAvailable;
        this.supply = Objects.requireNonNull(supply, "供应原子提交不可缺失");
    }

    /**
     * 从当前已发布且发现候选仍有效的连接创建草稿，不自动验证或授予租户使用权。
     *
     * @throws IllegalStateException 来源修订未验证、未发布或发现候选不可用时拒绝创建。
     */
    public CreatePlatformModelDeploymentResult create(CreatePlatformModelDeploymentCommand command) {
        Objects.requireNonNull(command, "平台模型部署创建命令不能为空");
        requirePermission(command.context());
        requireWrites();
        var connection = loadVerifiedConnection(command.providerConnectionId(), command.expectedConnectionVersion());
        requirePublishedConnection(connection);
        var snapshot = discoveries.findCurrentSnapshot(connection.id(), connection.version(), command.modelCode(),
                command.modelType(), clock.instant()).orElseThrow(() -> new IllegalStateException("平台模型发现候选不可用"));
        var candidate = snapshot.candidate();
        if (candidate.classification() == com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification.REQUIRES_ADMIN_CONFIRMATION) {
            if (connection.preset() == null || !connection.preset().capability().equals(command.modelType().name()))
                throw new IllegalArgumentException("确认类型必须属于连接声明的能力范围");
            candidate = new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate(
                    candidate.modelCode(), command.modelType(), java.util.Set.of(com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability.valueOf(command.modelType().name())),
                    com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification.CONFIRMED_BY_ADMIN, candidate.displayName());
        }
        if (candidate.modelType() != command.modelType() || !candidate.capabilities().equals(command.capabilities()))
            throw new IllegalArgumentException("平台模型发现候选不覆盖声明能力");
        var inputs = new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.deployment.PlatformModelDeploymentInputs(
                command.parameterSource(), connection.region(), command.rerankCandidateLimit(),
                command.plannedTenantIds().stream().map(Long::parseLong).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                snapshot.discoveryId(), candidate.displayName(), candidate.classification(), command.context().subjectId().value(), clock.instant());
        var deployment = PlatformModelDeployment.createDraft(new PlatformModelDeploymentId(identifiers.nextId()), connection.id(),
                connection.version(), command.modelCode(), command.displayName(), command.modelType(), command.capabilities(),
                command.contextWindow(), command.outputLimit(), command.embeddingDimension(), clock.instant(), inputs);
        var outcome = deployments.create(new PlatformModelDeploymentCreation(deployment, command.idempotencyKey(), fingerprint(command)));
        return new CreatePlatformModelDeploymentResult(toView(outcome.deployment(), connection, clock.instant()), outcome.replayed());
    }

    /** 只读辅助摘要；缺失不能补零或推导写入可用性。 */
    public com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment.PlatformDeploymentPageFacts facts(PlatformRequestContext context) {
        requirePermission(context);
        return new com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment.PlatformDeploymentPageFacts(writesAvailable, deployments.counts());
    }

    /** 只返回已发布连接当前完整发现根的候选，驻留和能力意图来自连接。 */
    public PageResult<com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment.PlatformDeployableModel> options(
            PlatformRequestContext context, com.aiplatform.foundation.shared.contract.paging.PageQuery page) {
        requirePermission(context);
        return discoveries.findDeployableModels(page, clock.instant());
    }

    private void requireWrites() {
        if (!writesAvailable) throw new com.aiplatform.foundation.shared.kernel.error.ApplicationException(
                com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode.S2_WRITES_PAUSED);
    }

    /** 只读取精确对象，禁止扫描列表或返回首项代替指定部署。 */
    public PlatformModelDeploymentView detail(com.aiplatform.foundation.security.identity.PlatformRequestContext context, String id) {
        requirePermission(context);
        var deployment = deployments.findById(PlatformModelDeploymentId.parse(id)).orElseThrow(
                () -> new com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentNotFoundException());
        return toView(deployment, connections.findById(deployment.providerConnectionId()).orElse(null), clock.instant());
    }

    public PageResult<PlatformModelDeploymentView> list(ListPlatformModelDeploymentsQuery query) {
        Objects.requireNonNull(query, "平台模型部署列表查询不能为空");
        requirePermission(query.context());
        var page = deployments.findPage(query.page().pageNumber(), query.page().pageSize());
        return PageResult.of(page.items().stream().map(deployment -> toView(deployment,
                connections.findById(deployment.providerConnectionId()).orElse(null), clock.instant())).toList(),
                query.page(), page.totalElements());
    }

    /**
     * 能力外呼前复核冻结来源的当前验证与独立发布事实，不能沿用旧草稿的创建检查。
     *
     * @throws IllegalStateException 来源修订变化、不可用或尚未发布时拒绝探测。
     */
    public ProbePlatformModelDeploymentResult probe(ProbePlatformModelDeploymentCommand command) {
        Objects.requireNonNull(command, "平台模型部署探测命令不能为空");
        requirePermission(command.context());
        requireWrites();
        var deployment = load(command.modelDeploymentId(), command.expectedVersion());
        if (deployment.inputs() == null) throw new com.aiplatform.foundation.shared.kernel.error.ApplicationException(
                com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode.PLATFORM_DEPLOYMENT_CONTRACT_PENDING);
        var connection = loadFrozenConnection(deployment);
        var result = probeProvider.probe(command, deployment, connection);
        return new ProbePlatformModelDeploymentResult(toView(result.deployment(), connection, result.at()),
                result.succeeded(), result.observed(), result.elapsed(), result.code(), result.at());
    }

    /**
     * 发布前复核冻结来源；原子供应目录和授权合同未接入时继续失败关闭。
     *
     * @throws IllegalStateException 来源失效、尚未发布或部署修订变化时拒绝发布。
     */
    public PlatformModelDeploymentView publish(PublishPlatformModelDeploymentCommand command) {
        Objects.requireNonNull(command, "平台模型部署发布命令不能为空");
        requirePermission(command.context());
        requireWrites();
        var deployment = load(command.modelDeploymentId(), command.expectedVersion());
        loadFrozenConnection(deployment);
        var published = supply.publish(command.context(), deployment, command.sessionActive());
        return toView(published, connections.findById(published.providerConnectionId()).orElse(null), clock.instant());
    }

    /** 停用部署以阻断新目录发布和运行时调用，保留既有验证审计。 */
    public PlatformModelDeploymentView disable(DisablePlatformModelDeploymentCommand command) {
        Objects.requireNonNull(command, "平台模型部署停用命令不能为空");
        requirePermission(command.context());
        var deployment = load(command.modelDeploymentId(), command.expectedVersion());
        var disabled = supply.disable(command.context(), deployment, command.sessionActive());
        return toView(disabled, null, clock.instant());
    }

    /** 计划选择与活动授权分别返回，只有独立发布才具有正式供应事实。 */
    public java.util.Optional<com.aiplatform.foundation.modelruntime.application.platformsupply.dto.publication.PlatformSupplyFacts> supplyFacts(
            PlatformRequestContext context, String id) {
        requirePermission(context);
        var deploymentId = PlatformModelDeploymentId.parse(id);
        deployments.findById(deploymentId).orElseThrow(com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentNotFoundException::new);
        return supply.facts(deploymentId);
    }

    /** 精确撤销一个租户供应关系；安全收紧不受新增写入暂停限制。 */
    public void revokeSupply(PlatformRequestContext context, String id, long tenantId, long expectedVersion,
            long expectedGrantVersion, java.util.function.BooleanSupplier session) {
        requirePermission(context);
        supply.revoke(context, PlatformModelDeploymentId.parse(id), tenantId, expectedVersion, expectedGrantVersion, session);
    }

    private PlatformModelDeployment load(String id, long expectedVersion) {
        var deployment = deployments.findById(PlatformModelDeploymentId.parse(id))
                .orElseThrow(() -> new IllegalArgumentException("平台模型部署不存在"));
        if (deployment.version() != expectedVersion)
            throw new com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentConcurrentModificationException();
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

    /** 验证与发布各自持有事实；只有当前连接修订的明确发布才能作为新部署来源。 */
    private void requirePublishedConnection(PlatformProviderConnection connection) {
        var published = publications.find(connection.id(), connection.version())
                .filter(publication -> publication.connectionId().equals(connection.id())
                        && publication.connectionRevision() == connection.version());
        if (published.isEmpty()) throw new IllegalStateException("平台供应商连接当前修订尚未发布");
    }

    private PlatformProviderConnection loadFrozenConnection(PlatformModelDeployment deployment) {
        var connection = loadVerifiedConnection(deployment.providerConnectionId().toString(), deployment.providerConnectionRevision());
        requirePublishedConnection(connection);
        return connection;
    }

    private static void requirePermission(PlatformRequestContext context) {
        context = Objects.requireNonNull(context, "平台请求上下文不能为空");
        if (!context.hasPermission(PlatformPermissions.MODEL_PLATFORM_WRITE)) throw new ModelCatalogPermissionDeniedException();
    }

    private PlatformModelDeploymentView toView(PlatformModelDeployment deployment,
            PlatformProviderConnection connection, java.time.Instant now) {
        var supplyFacts = supply.facts(deployment.id()).orElse(null);
        var availability = availability(deployment, connection, now);
        if (deployment.status() == PlatformModelDeploymentStatus.PUBLISHED
                && (supplyFacts == null || !supplyFacts.currentSourceAvailable()))
            availability = new Availability(ModelDeploymentAvailability.UNAVAILABLE, ModelDeploymentAvailabilityNextStep.NONE);
        return new PlatformModelDeploymentView(deployment.id().toString(), deployment.providerConnectionId().toString(),
                deployment.providerConnectionRevision(), deployment.modelCode(), deployment.displayName(), deployment.modelType().name(),
                deployment.capabilities(), deployment.contextWindow(), deployment.outputLimit(), deployment.embeddingDimension(),
                deployment.verificationExpiresAt(), deployment.status().name(), availability.status(), availability.nextStep(),
                deployment.version(), deployment.createdAt(), connection == null ? "来源连接暂未取得" : connection.displayName(),
                com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment.PlatformModelDeploymentInputsView.from(deployment.inputs()),
                deployment.lastVerificationId() == null ? null : deployments.lastVerifiedAt(deployment.id()).orElse(null), supplyFacts);
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
            for (var value : java.util.List.of(command.providerConnectionId(), command.modelCode(), command.displayName(), command.modelType().name(), Integer.toString(command.contextWindow()), Objects.toString(command.outputLimit(), ""), Objects.toString(command.embeddingDimension(), ""), Long.toString(command.expectedConnectionVersion()),
                    Objects.toString(command.rerankCandidateLimit(), ""), command.parameterSource(), command.modelConfirmed().toString(), command.context().subjectId().toString())) update(digest, value);
            command.plannedTenantIds().stream().sorted().forEach(value -> update(digest, value));
            command.capabilities().stream().sorted(Comparator.comparing(Enum::name)).forEach(capability -> update(digest, capability.name()));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception); }
    }
    private static void update(MessageDigest digest, String value) { digest.update(value.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0); }
    private record Availability(ModelDeploymentAvailability status, ModelDeploymentAvailabilityNextStep nextStep) {}
}

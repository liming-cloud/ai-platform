package com.aiplatform.foundation.modelruntime.application.deployment.service;

import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionNotFoundException;
import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentNotFoundException;
import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentProviderUnavailableException;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.modelruntime.application.deployment.command.CreateModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.CreateModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.application.deployment.query.ListModelDeploymentsQuery;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentView;
import com.aiplatform.foundation.modelruntime.application.deployment.command.ProbeModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ProbeModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.application.deployment.command.PublishModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.deployment.port.out.ModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeRequest;
import com.aiplatform.foundation.modelruntime.application.probe.service.ProbeProviderService;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityVerificationId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityObservation;
import com.aiplatform.foundation.modelruntime.domain.deployment.service.ModelDeploymentVerification;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 在可信租户、供应商修订和乐观版本边界内编排模型部署。
 */
public final class ModelDeploymentService {

    private static final PermissionCode READ = new PermissionCode("model:read");
    private static final PermissionCode WRITE = new PermissionCode("model:write");

    private final ModelDeploymentRepository deployments;
    private final ProviderConnectionRepository providers;
    private final ProbeProviderService probeProvider;
    private final ModelDeploymentVerificationStore verificationStore;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final int tenantLimit;
    private final Duration verificationValidity;

    public ModelDeploymentService(
            ModelDeploymentRepository deployments,
            ProviderConnectionRepository providers,
            ProbeProviderService probeProvider,
            ModelDeploymentVerificationStore verificationStore,
            Clock clock,
            EntityIdGenerator identifiers,
            int tenantLimit,
            Duration verificationValidity) {
        this.deployments = Objects.requireNonNull(deployments, "模型部署仓储不能为空");
        this.providers = Objects.requireNonNull(providers, "供应商连接仓储不能为空");
        this.probeProvider = Objects.requireNonNull(probeProvider, "供应商探测用例不能为空");
        this.verificationStore = Objects.requireNonNull(verificationStore, "模型能力验证存储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户模型部署配额必须大于零");
        }
        this.tenantLimit = tenantLimit;
        this.verificationValidity = requirePositive(
                verificationValidity, "模型能力验证有效期");
    }

    public CreateModelDeploymentResult create(CreateModelDeploymentCommand command) {
        Objects.requireNonNull(command, "创建命令不能为空");
        requirePermission(command.context(), WRITE);
        var provider = providers
                .findById(
                        command.context().tenantId(),
                        ProviderConnectionId.parse(command.providerConnectionId()))
                .orElseThrow(ProviderConnectionNotFoundException::new);
        requireVerifiedProvider(provider, provider.version());
        var deployment = ModelDeployment.create(
                command.context().tenantId(),
                new ModelDeploymentId(identifiers.nextId()),
                provider.id(),
                provider.version(),
                command.modelCode(),
                command.displayName(),
                command.modelType(),
                command.capabilities(),
                command.contextWindow(),
                command.outputLimit(),
                command.embeddingDimension(),
                clock.instant());
        var outcome = deployments.create(
                new ModelDeploymentCreation(
                        deployment,
                        command.idempotencyKey(),
                        fingerprint(command)),
                tenantLimit);
        return new CreateModelDeploymentResult(
                toView(outcome.deployment()),
                outcome.replayed());
    }

    public PageResult<ModelDeploymentView> list(ListModelDeploymentsQuery query) {
        Objects.requireNonNull(query, "列表查询不能为空");
        requirePermission(query.context(), READ);
        var slice = deployments.findPage(
                query.context().tenantId(),
                query.page().pageNumber(),
                query.page().pageSize());
        return PageResult.of(
                slice.items().stream()
                        .map(ModelDeploymentService::toView)
                        .toList(),
                query.page(),
                slice.totalElements());
    }

    public ProbeModelDeploymentResult probe(ProbeModelDeploymentCommand command) {
        Objects.requireNonNull(command, "探测命令不能为空");
        requirePermission(command.context(), WRITE);
        var deployment = load(command.context(), command.modelDeploymentId());
        requireExpectedVersion(deployment, command.expectedVersion());
        var provider = loadFrozenProvider(command.context(), deployment);
        var result = probeProvider.probe(new ProviderProbeRequest(
                command.context().tenantId(),
                provider.providerKind(),
                provider.endpoint(),
                provider.credentialReference(),
                deployment.modelCode(),
                deployment.capabilities(),
                command.timeout()));
        var verification = ModelDeploymentVerification.verify(
                deployment,
                new ModelCapabilityVerificationId(identifiers.nextId()),
                new ModelCapabilityObservation(
                        result.reachable(),
                        result.capabilities(),
                        result.detailCode(),
                        result.elapsed(),
                        result.probedAt()),
                result.probedAt().plus(verificationValidity));
        verificationStore.commit(verification, command.expectedVersion());
        return new ProbeModelDeploymentResult(
                toView(verification.revisedDeployment()),
                verification.verified(),
                result.capabilities(),
                result.elapsed(),
                verification.verification().resultCode(),
                result.probedAt());
    }

    public ModelDeploymentView publish(PublishModelDeploymentCommand command) {
        Objects.requireNonNull(command, "发布命令不能为空");
        requirePermission(command.context(), WRITE);
        var deployment = load(command.context(), command.modelDeploymentId());
        requireExpectedVersion(deployment, command.expectedVersion());
        loadFrozenProvider(command.context(), deployment);
        var published = deployment.publish(clock.instant());
        update(published, command.expectedVersion());
        return toView(published);
    }

    private ModelDeployment load(
            TrustedRequestContext context,
            String deploymentId) {
        return deployments
                .findById(context.tenantId(), ModelDeploymentId.parse(deploymentId))
                .orElseThrow(ModelDeploymentNotFoundException::new);
    }

    private ProviderConnection loadFrozenProvider(
            TrustedRequestContext context,
            ModelDeployment deployment) {
        var provider = providers
                .findById(context.tenantId(), deployment.providerConnectionId())
                .orElseThrow(ModelDeploymentProviderUnavailableException::new);
        requireVerifiedProvider(provider, deployment.providerConnectionRevision());
        return provider;
    }

    private static void requireVerifiedProvider(
            ProviderConnection provider,
            long expectedRevision) {
        if (provider.status() != ProviderConnectionStatus.VERIFIED
                || provider.version() != expectedRevision) {
            throw new ModelDeploymentProviderUnavailableException();
        }
    }

    private static void requireExpectedVersion(
            ModelDeployment deployment,
            long expectedVersion) {
        if (deployment.version() != expectedVersion) {
            throw new ModelDeploymentConcurrentModificationException();
        }
    }

    private void update(ModelDeployment deployment, long expectedVersion) {
        if (!deployments.update(deployment, expectedVersion)) {
            throw new ModelDeploymentConcurrentModificationException();
        }
    }

    private static void requirePermission(
            TrustedRequestContext context,
            PermissionCode permission) {
        if (!context.hasPermission(permission)) {
            throw new ModelCatalogPermissionDeniedException();
        }
    }

    private static Duration requirePositive(Duration value, String field) {
        value = Objects.requireNonNull(value, field + "不能为空");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(field + "必须大于零");
        }
        return value;
    }

    private static ModelDeploymentView toView(ModelDeployment deployment) {
        return new ModelDeploymentView(
                deployment.id().toString(),
                deployment.providerConnectionId().toString(),
                deployment.providerConnectionRevision(),
                deployment.modelCode(),
                deployment.displayName(),
                deployment.modelType().name(),
                deployment.capabilities(),
                deployment.contextWindow(),
                deployment.outputLimit(),
                deployment.embeddingDimension(),
                deployment.status().name(),
                deployment.version(),
                deployment.createdAt());
    }

    private static String fingerprint(CreateModelDeploymentCommand command) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, command.providerConnectionId().toString());
            update(digest, command.modelCode());
            update(digest, command.displayName());
            update(digest, command.modelType().name());
            command.capabilities().stream()
                    .sorted(Comparator.comparing(Enum::name))
                    .forEach(capability -> update(digest, capability.name()));
            update(digest, Integer.toString(command.contextWindow()));
            update(digest, Objects.toString(command.outputLimit(), ""));
            update(digest, Objects.toString(command.embeddingDimension(), ""));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}

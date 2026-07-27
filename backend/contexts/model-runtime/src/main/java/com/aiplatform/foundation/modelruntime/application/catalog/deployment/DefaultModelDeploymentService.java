package com.aiplatform.foundation.modelruntime.application.catalog.deployment;

import com.aiplatform.foundation.modelruntime.application.catalog.common.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.application.catalog.provider.ProviderConnectionNotFoundException;
import com.aiplatform.foundation.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.modelruntime.api.catalog.ModelDeploymentUseCase;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.CreateModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.CreateModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.ListModelDeploymentsQuery;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.ModelDeploymentPage;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.ModelDeploymentView;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.ProbeModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.ProbeModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.PublishModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderProbeRequest;
import com.aiplatform.foundation.modelruntime.api.probe.ProbeProviderUseCase;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionStatus;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 在可信租户、供应商修订和乐观版本边界内编排模型部署。
 */
public final class DefaultModelDeploymentService implements ModelDeploymentUseCase {

    private static final PermissionCode READ = new PermissionCode("model:read");
    private static final PermissionCode WRITE = new PermissionCode("model:write");

    private final ModelDeploymentRepository deployments;
    private final ProviderConnectionRepository providers;
    private final ProbeProviderUseCase probeProvider;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final int tenantLimit;

    public DefaultModelDeploymentService(
            ModelDeploymentRepository deployments,
            ProviderConnectionRepository providers,
            ProbeProviderUseCase probeProvider,
            Clock clock,
            EntityIdGenerator identifiers,
            int tenantLimit) {
        this.deployments = Objects.requireNonNull(deployments, "模型部署仓储不能为空");
        this.providers = Objects.requireNonNull(providers, "供应商连接仓储不能为空");
        this.probeProvider = Objects.requireNonNull(probeProvider, "供应商探测用例不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户模型部署配额必须大于零");
        }
        this.tenantLimit = tenantLimit;
    }

    @Override
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

    @Override
    public ModelDeploymentPage list(ListModelDeploymentsQuery query) {
        Objects.requireNonNull(query, "列表查询不能为空");
        requirePermission(query.context(), READ);
        var slice = deployments.findPage(
                query.context().tenantId(),
                query.cursor(),
                query.limit());
        return new ModelDeploymentPage(
                slice.items().stream()
                        .map(DefaultModelDeploymentService::toView)
                        .toList(),
                slice.nextCursor());
    }

    @Override
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
        if (!result.reachable()) {
            return new ProbeModelDeploymentResult(
                    toView(deployment),
                    false,
                    result.capabilities(),
                    result.elapsed(),
                    result.detailCode(),
                    result.probedAt());
        }
        if (!result.capabilities().containsAll(deployment.capabilities())) {
            return new ProbeModelDeploymentResult(
                    toView(deployment),
                    false,
                    result.capabilities(),
                    result.elapsed(),
                    "MODEL_CAPABILITY_MISMATCH",
                    result.probedAt());
        }
        var verified = deployment.verify();
        update(verified, command.expectedVersion());
        return new ProbeModelDeploymentResult(
                toView(verified),
                true,
                result.capabilities(),
                result.elapsed(),
                result.detailCode(),
                result.probedAt());
    }

    @Override
    public ModelDeploymentView publish(PublishModelDeploymentCommand command) {
        Objects.requireNonNull(command, "发布命令不能为空");
        requirePermission(command.context(), WRITE);
        var deployment = load(command.context(), command.modelDeploymentId());
        requireExpectedVersion(deployment, command.expectedVersion());
        loadFrozenProvider(command.context(), deployment);
        var published = deployment.publish();
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

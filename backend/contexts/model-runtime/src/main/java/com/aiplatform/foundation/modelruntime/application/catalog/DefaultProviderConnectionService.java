package com.aiplatform.foundation.modelruntime.application.catalog;

import com.aiplatform.foundation.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.modelruntime.api.catalog.ProviderConnectionUseCase;
import com.aiplatform.foundation.modelruntime.api.catalog.model.CreateProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.CreateProviderConnectionResult;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ListProviderConnectionsQuery;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ProbeProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ProbeProviderConnectionResult;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ProviderConnectionPage;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ProviderConnectionView;
import com.aiplatform.foundation.modelruntime.api.model.ProviderProbeRequest;
import com.aiplatform.foundation.modelruntime.api.probe.ProbeProviderUseCase;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import com.aiplatform.foundation.security.secret.SecretReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 在可信租户和权限边界内编排供应商连接。
 */
public final class DefaultProviderConnectionService implements ProviderConnectionUseCase {

    private static final PermissionCode READ = new PermissionCode("model:read");
    private static final PermissionCode WRITE = new PermissionCode("model:write");

    private final ProviderConnectionRepository repository;
    private final ProbeProviderUseCase probeProvider;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final int tenantLimit;

    public DefaultProviderConnectionService(
            ProviderConnectionRepository repository,
            ProbeProviderUseCase probeProvider,
            Clock clock,
            EntityIdGenerator identifiers,
            int tenantLimit) {
        this.repository = Objects.requireNonNull(repository, "供应商连接仓储不能为空");
        this.probeProvider = Objects.requireNonNull(probeProvider, "供应商探测用例不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户供应商连接配额必须大于零");
        }
        this.tenantLimit = tenantLimit;
    }

    @Override
    public CreateProviderConnectionResult create(CreateProviderConnectionCommand command) {
        Objects.requireNonNull(command, "创建命令不能为空");
        requirePermission(command.context(), WRITE);
        var connection = ProviderConnection.create(
                command.context().tenantId(),
                new ProviderConnectionId(identifiers.nextId()),
                command.providerKind(),
                command.displayName(),
                command.endpoint(),
                command.region(),
                command.authenticationType(),
                new SecretReference(command.credentialReference()),
                clock.instant());
        var outcome = repository.create(
                new ProviderConnectionCreation(
                        connection,
                        command.idempotencyKey(),
                        fingerprint(command)),
                tenantLimit);
        return new CreateProviderConnectionResult(
                toView(outcome.connection()),
                outcome.replayed());
    }

    @Override
    public ProviderConnectionPage list(ListProviderConnectionsQuery query) {
        Objects.requireNonNull(query, "列表查询不能为空");
        requirePermission(query.context(), READ);
        var slice = repository.findPage(
                query.context().tenantId(),
                query.cursor(),
                query.limit());
        return new ProviderConnectionPage(
                slice.items().stream()
                        .map(DefaultProviderConnectionService::toView)
                        .toList(),
                slice.nextCursor());
    }

    @Override
    public ProbeProviderConnectionResult probe(ProbeProviderConnectionCommand command) {
        Objects.requireNonNull(command, "探测命令不能为空");
        requirePermission(command.context(), WRITE);
        var connectionId = ProviderConnectionId.parse(command.providerConnectionId());
        var connection = repository
                .findById(command.context().tenantId(), connectionId)
                .orElseThrow(ProviderConnectionNotFoundException::new);
        if (connection.version() != command.expectedVersion()) {
            throw new ProviderConnectionConcurrentModificationException();
        }
        var result = probeProvider.probe(new ProviderProbeRequest(
                command.context().tenantId(),
                connection.providerKind(),
                connection.endpoint(),
                connection.credentialReference(),
                command.modelCode(),
                java.util.Set.of(),
                command.timeout()));
        var updated = result.reachable()
                ? connection.recordSuccessfulProbe(result.detailCode(), result.probedAt())
                : connection.recordFailedProbe(result.detailCode(), result.probedAt());
        if (!repository.update(updated, command.expectedVersion())) {
            throw new ProviderConnectionConcurrentModificationException();
        }
        return new ProbeProviderConnectionResult(
                toView(updated),
                result.reachable(),
                result.capabilities(),
                result.elapsed(),
                result.detailCode(),
                result.probedAt());
    }

    private static void requirePermission(
            TrustedRequestContext context,
            PermissionCode permission) {
        if (!context.hasPermission(permission)) {
            throw new ModelCatalogPermissionDeniedException();
        }
    }

    private static ProviderConnectionView toView(ProviderConnection connection) {
        return new ProviderConnectionView(
                connection.id().toString(),
                connection.providerKind().name(),
                connection.displayName(),
                connection.endpoint(),
                connection.region(),
                connection.authenticationType().name(),
                true,
                connection.status().name(),
                connection.version(),
                connection.lastProbeSucceeded(),
                connection.lastProbeCode(),
                connection.lastProbedAt(),
                connection.createdAt());
    }

    private static String fingerprint(CreateProviderConnectionCommand command) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, command.providerKind().name());
            update(digest, command.displayName());
            update(digest, command.endpoint().toString());
            update(digest, command.region());
            update(digest, command.authenticationType().name());
            update(digest, command.credentialReference());
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

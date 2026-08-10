package com.aiplatform.foundation.modelruntime.application.provider.service;

import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionNotFoundException;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.modelruntime.application.provider.command.CreateProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.provider.dto.CreateProviderConnectionResult;
import com.aiplatform.foundation.modelruntime.application.provider.query.ListProviderConnectionsQuery;
import com.aiplatform.foundation.modelruntime.application.provider.command.ProbeProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.provider.dto.ProbeProviderConnectionResult;
import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderConnectionView;
import com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeRequest;
import com.aiplatform.foundation.modelruntime.application.probe.service.ProbeProviderService;
import com.aiplatform.foundation.modelruntime.application.provider.command.RotateProviderConnectionCredentialCommand;
import com.aiplatform.foundation.modelruntime.application.provider.dto.RotateProviderConnectionCredentialResult;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderConnectionCredentialTransaction;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.service.ProviderConnectionCredentialProvisioning;
import com.aiplatform.foundation.modelruntime.domain.provider.service.ProviderConnectionCredentialRotation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 在可信租户和权限边界内编排供应商连接。
 */
public final class ProviderConnectionService {

    private static final PermissionCode READ = new PermissionCode("model:read");
    private static final PermissionCode WRITE = new PermissionCode("model:write");

    private final ProviderConnectionRepository repository;
    private final ProbeProviderService probeProvider;
    private final ProviderConnectionCredentialWriter credentials;
    private final ProviderConnectionCredentialTransaction transactions;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final int tenantLimit;

    public ProviderConnectionService(
            ProviderConnectionRepository repository,
            ProbeProviderService probeProvider,
            ProviderConnectionCredentialWriter credentials,
            ProviderConnectionCredentialTransaction transactions,
            Clock clock,
            EntityIdGenerator identifiers,
            int tenantLimit) {
        this.repository = Objects.requireNonNull(repository, "供应商连接仓储不能为空");
        this.probeProvider = Objects.requireNonNull(probeProvider, "供应商探测用例不能为空");
        this.credentials = Objects.requireNonNull(credentials, "供应商连接凭据写入器不能为空");
        this.transactions = Objects.requireNonNull(transactions, "供应商连接凭据事务不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户供应商连接配额必须大于零");
        }
        this.tenantLimit = tenantLimit;
    }

    public CreateProviderConnectionResult create(CreateProviderConnectionCommand command) {
        Objects.requireNonNull(command, "创建命令不能为空");
        requirePermission(command.context(), WRITE);
        var connectionId = new ProviderConnectionId(identifiers.nextId());
        var credentialReference = credentialReference(connectionId);
        var operation = new ProviderConnectionCredentialProvisioning().provision(
                command.context().tenantId(),
                connectionId,
                command.providerKind(),
                command.displayName(),
                command.endpoint(),
                command.region(),
                command.authenticationType(),
                credentialReference,
                clock.instant());
        var outcome = transactions.execute(() -> {
            var created = repository.create(
                    new ProviderConnectionCreation(
                            operation.connection(),
                            command.idempotencyKey(),
                            fingerprint(command)),
                    tenantLimit);
            if (!created.replayed()) {
                credentials.write(command.context().tenantId(), credentialReference, command.apiKey());
            }
            return created;
        });
        return new CreateProviderConnectionResult(
                toView(outcome.connection()),
                outcome.replayed());
    }

    public PageResult<ProviderConnectionView> list(ListProviderConnectionsQuery query) {
        Objects.requireNonNull(query, "列表查询不能为空");
        requirePermission(query.context(), READ);
        var slice = repository.findPage(
                query.context().tenantId(),
                query.page().pageNumber(),
                query.page().pageSize());
        return PageResult.of(
                slice.items().stream()
                        .map(ProviderConnectionService::toView)
                        .toList(),
                query.page(),
                slice.totalElements());
    }

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

    /**
     * 轮换秘密会使当前连接修订的全部成功探测失效。
     */
    public RotateProviderConnectionCredentialResult rotateCredential(
            RotateProviderConnectionCredentialCommand command) {
        Objects.requireNonNull(command, "轮换命令不能为空");
        requirePermission(command.context(), WRITE);
        var connectionId = ProviderConnectionId.parse(command.providerConnectionId());
        return transactions.execute(() -> {
            var connection = repository.findById(command.context().tenantId(), connectionId)
                    .orElseThrow(ProviderConnectionNotFoundException::new);
            if (connection.version() != command.expectedVersion()) {
                throw new ProviderConnectionConcurrentModificationException();
            }
            var operation = new ProviderConnectionCredentialRotation().rotate(
                    connection, credentialReference(connectionId));
            if (!repository.update(operation.connection(), command.expectedVersion())) {
                throw new ProviderConnectionConcurrentModificationException();
            }
            credentials.write(
                    command.context().tenantId(),
                    operation.connection().credentialReference(),
                    command.apiKey());
            return new RotateProviderConnectionCredentialResult(toView(operation.connection()));
        });
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
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static CredentialReference credentialReference(ProviderConnectionId connectionId) {
        return new CredentialReference("secret://provider-connection/" + connectionId.value());
    }
}

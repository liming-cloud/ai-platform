package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.CreatePlatformProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.ProbePlatformProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderProbeRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.CreatePlatformProviderConnectionResult;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderConnectionView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialTransaction;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.application.platformsupply.query.ListPlatformProviderConnectionsQuery;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

/** 编排平台连接的授权、幂等创建与秘密原子写入。 */
public final class PlatformProviderConnectionService {

    private static final String CREDENTIAL_PREFIX = "secret://platform-provider-connection/";
    private static final String DISCOVERY_ACCESS_KEY_PREFIX =
            "secret://platform-provider-discovery-access-key/";
    private static final String DISCOVERY_SECRET_KEY_PREFIX =
            "secret://platform-provider-discovery-secret-key/";

    private final PlatformProviderConnectionRepository repository;
    private final PlatformProviderConnectionCredentialWriter credentials;
    private final PlatformProviderConnectionCredentialTransaction transactions;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final PlatformProbeProviderService probeProvider;

    public PlatformProviderConnectionService(
            PlatformProviderConnectionRepository repository,
            PlatformProviderConnectionCredentialWriter credentials,
            PlatformProviderConnectionCredentialTransaction transactions,
            PlatformProbeProviderService probeProvider,
            Clock clock,
            EntityIdGenerator identifiers) {
        this.repository = Objects.requireNonNull(repository, "平台供应商连接仓储不能为空");
        this.credentials = Objects.requireNonNull(credentials, "平台供应商连接凭据写入器不能为空");
        this.transactions = Objects.requireNonNull(transactions, "平台供应商连接事务不能为空");
        this.probeProvider = Objects.requireNonNull(probeProvider, "平台供应商探测用例不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
    }

    public CreatePlatformProviderConnectionResult create(
            CreatePlatformProviderConnectionCommand command) {
        Objects.requireNonNull(command, "创建命令不能为空");
        requirePermission(command.context());
        var connectionId = new PlatformProviderConnectionId(identifiers.nextId());
        var connection = PlatformProviderConnection.create(
                connectionId,
                command.providerKind(),
                command.discoveryProfile(),
                command.displayName(),
                command.endpoint(),
                command.region(),
                command.authenticationType(),
                new com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference(
                        CREDENTIAL_PREFIX + connectionId.value()),
                clock.instant());
        var outcome = transactions.execute(() -> {
            var created = repository.create(new PlatformProviderConnectionCreation(
                    connection, command.idempotencyKey(), fingerprint(command)));
            if (!created.replayed()) {
                credentials.write(connection.credentialReference(), command.apiKey());
                if (command.discoveryAccessKeyId() != null) {
                    credentials.write(credentialReference(DISCOVERY_ACCESS_KEY_PREFIX, connectionId),
                            command.discoveryAccessKeyId());
                    credentials.write(credentialReference(DISCOVERY_SECRET_KEY_PREFIX, connectionId),
                            command.discoverySecretAccessKey());
                }
            }
            return created;
        });
        return new CreatePlatformProviderConnectionResult(toView(outcome.connection()), outcome.replayed());
    }

    private static com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference
            credentialReference(String prefix, PlatformProviderConnectionId connectionId) {
        return new com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference(
                prefix + connectionId.value());
    }

    public PageResult<PlatformProviderConnectionView> list(
            ListPlatformProviderConnectionsQuery query) {
        Objects.requireNonNull(query, "列表查询不能为空");
        requirePermission(query.context());
        var slice = repository.findPage(query.page().pageNumber(), query.page().pageSize());
        return PageResult.of(slice.items().stream().map(PlatformProviderConnectionService::toView).toList(),
                query.page(), slice.totalElements());
    }

    /** 平台探测只处理平台连接及其独立密钥引用，不得引入租户上下文。 */
    public PlatformProviderConnectionView probe(ProbePlatformProviderConnectionCommand command) {
        Objects.requireNonNull(command, "平台连接探测命令不能为空");
        requirePermission(command.context());
        var connection = repository.findById(PlatformProviderConnectionId.parse(command.providerConnectionId()))
                .orElseThrow(() -> new IllegalArgumentException("平台供应商连接不存在"));
        if (connection.version() != command.expectedVersion()) {
            throw new IllegalStateException("平台供应商连接修订已变化");
        }
        var result = probeProvider.probe(new PlatformProviderProbeRequest(
                connection.providerKind(), connection.endpoint(), connection.credentialReference(),
                command.modelCode(), command.timeout()));
        var updated = result.reachable()
                ? connection.recordSuccessfulProbe(result.detailCode(), result.probedAt())
                : connection.recordFailedProbe(result.detailCode(), result.probedAt());
        if (!repository.update(updated, command.expectedVersion())) {
            throw new IllegalStateException("平台供应商连接修订已变化");
        }
        return toView(updated);
    }

    private static void requirePermission(PlatformRequestContext context) {
        if (!context.hasPermission(PlatformPermissions.MODEL_PLATFORM_WRITE)) {
            throw new ModelCatalogPermissionDeniedException();
        }
    }

    private static PlatformProviderConnectionView toView(PlatformProviderConnection connection) {
        return new PlatformProviderConnectionView(
                connection.id().toString(), connection.providerKind().name(), connection.displayName(),
                connection.discoveryProfile().name(),
                connection.region(), connection.authenticationType().name(), true, connection.status().name(),
                connection.version(), connection.lastProbeSucceeded(), connection.lastProbeCode(),
                connection.lastProbedAt(), connection.createdAt());
    }

    private static String fingerprint(CreatePlatformProviderConnectionCommand command) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, command.providerKind().name());
            update(digest, command.discoveryProfile().name());
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
}

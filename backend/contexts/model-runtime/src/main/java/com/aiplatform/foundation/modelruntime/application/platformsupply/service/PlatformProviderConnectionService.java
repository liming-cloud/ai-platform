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
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionNotFoundException;
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

    private final PlatformProviderConnectionWritePolicy writes;
    private final PlatformProviderConnectionRepository repository;
    private final PlatformProviderConnectionCredentialWriter credentials;
    private final PlatformProviderConnectionCredentialTransaction transactions;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final PlatformProbeProviderService probeProvider;
    private final com.aiplatform.foundation.security.egress.OutboundEndpointPolicy endpointPolicy;
    private final com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionFacts facts;

    public PlatformProviderConnectionService(
            PlatformProviderConnectionRepository repository,
            PlatformProviderConnectionCredentialWriter credentials,
            PlatformProviderConnectionCredentialTransaction transactions,
            PlatformProbeProviderService probeProvider,
            Clock clock,
            EntityIdGenerator identifiers,
            com.aiplatform.foundation.security.egress.OutboundEndpointPolicy endpointPolicy,
            com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionFacts facts,
            PlatformProviderConnectionWritePolicy writes) {
        this.writes = Objects.requireNonNull(writes, "连接写入策略不能为空");
        this.repository = Objects.requireNonNull(repository, "平台供应商连接仓储不能为空");
        this.credentials = Objects.requireNonNull(credentials, "平台供应商连接凭据写入器不能为空");
        this.transactions = Objects.requireNonNull(transactions, "平台供应商连接事务不能为空");
        this.probeProvider = Objects.requireNonNull(probeProvider, "平台供应商探测用例不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "出站安全策略不能为空");
        this.facts = Objects.requireNonNull(facts, "平台连接事实源不能为空");
    }

    public CreatePlatformProviderConnectionResult create(
            CreatePlatformProviderConnectionCommand command) {
        Objects.requireNonNull(command, "创建命令不能为空");
        requirePermission(command.context());
        writes.requireAvailable();
        PlatformProviderPresets.validate(command.preset(), command.providerKind(), command.discoveryProfile(),
                command.endpoint(), command.region());
        // 草稿也不能保存禁止地址；探测时仍会再次校验，不能复用本次 DNS 结论。
        endpointPolicy.verify(command.endpoint());
        var connectionId = new PlatformProviderConnectionId(identifiers.nextId());
        var connection = PlatformProviderConnection.create(
                connectionId,
                command.providerKind(),
                command.discoveryProfile(),
                command.preset(),
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
        var slice = repository.findPage(query.page().pageNumber(), query.page().pageSize(), query.q());
        var currentFacts = facts.find(slice.items().stream().map(connection -> connection.id().value())
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        return PageResult.of(slice.items().stream().map(connection -> toView(connection, currentFacts.get(connection.id().value()))).toList(),
                query.page(), slice.totalElements());
    }

    /** 平台权限通过后只统计独立发布事实，不解析秘密或向厂商外呼。 */
    public com.aiplatform.foundation.modelruntime.application.platformsupply.dto.directory.PlatformProviderConnectionDirectorySummary summary(
            PlatformRequestContext context) {
        requirePermission(context);
        return new com.aiplatform.foundation.modelruntime.application.platformsupply.dto.directory.PlatformProviderConnectionDirectorySummary(repository.countPublished());
    }

    /** 平台预设是受控非秘密配置，读取仍要求可信平台身份。 */
    public com.aiplatform.foundation.modelruntime.application.platformsupply.dto.preset.PlatformProviderPresetsView presets(
            PlatformRequestContext context) {
        requirePermission(Objects.requireNonNull(context, "平台请求上下文不能为空"));
        var presets = PlatformProviderPresets.view();
        return new com.aiplatform.foundation.modelruntime.application.platformsupply.dto.preset.PlatformProviderPresetsView(
                presets.version(), presets.items(), writes.writesAvailable());
    }

    /** 只读恢复平台连接；先检查平台权限，不读取凭据、写入探测或建立发布事实。 */
    public PlatformProviderConnectionView get(PlatformRequestContext context, String providerConnectionId) {
        requirePermission(Objects.requireNonNull(context, "平台请求上下文不能为空"));
        return toView(repository.findById(PlatformProviderConnectionId.parse(providerConnectionId))
                .orElseThrow(ProviderConnectionNotFoundException::new));
    }

    /** 平台探测只处理平台连接及其独立密钥引用，不得引入租户上下文。 */
    public PlatformProviderConnectionView probe(ProbePlatformProviderConnectionCommand command) {
        Objects.requireNonNull(command, "平台连接探测命令不能为空");
        requirePermission(command.context());
        writes.requireAvailable();
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

    private PlatformProviderConnectionView toView(PlatformProviderConnection connection) {
        return toView(connection, facts.find(java.util.Set.of(connection.id().value())).get(connection.id().value()));
    }

    private static PlatformProviderConnectionView toView(PlatformProviderConnection connection,
            com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionFacts.Fact fact) {
        // 分开读取的摘要若遇到并发更新，要求重新读取，不能混用两个修订的状态。
        if (fact == null || fact.connectionId() != connection.id().value() || fact.version() != connection.version()) {
            throw new com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException();
        }
        return new PlatformProviderConnectionView(
                connection.id().toString(), connection.providerKind().name(), connection.displayName(),
                connection.discoveryProfile().name(),
                connection.region(), connection.authenticationType().name(), fact.credentialConfigured(), connection.status().name(),
                connection.version(), connection.lastProbeSucceeded(), connection.lastProbeCode(),
                connection.lastProbedAt(), connection.createdAt(), connection.preset() == null ? null
                        : new PlatformProviderConnectionView.Preset(connection.preset().providerId(),
                                connection.preset().version(), connection.preset().capability()),
                fact.discoveryCredentialsConfigured(), fact.published());
    }

    private static String fingerprint(CreatePlatformProviderConnectionCommand command) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, command.providerKind().name());
            update(digest, command.discoveryProfile().name());
            update(digest, command.preset().providerId());
            update(digest, command.preset().version());
            update(digest, command.preset().capability());
            update(digest, command.preset().workspaceId() == null ? "" : command.preset().workspaceId());
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

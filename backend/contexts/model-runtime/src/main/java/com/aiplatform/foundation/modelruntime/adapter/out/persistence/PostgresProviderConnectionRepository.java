package com.aiplatform.foundation.modelruntime.adapter.out.persistence;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.catalog.provider.ProviderConnectionIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.provider.ProviderConnectionNameConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.provider.ProviderConnectionQuotaExceededException;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionCreationOutcome;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionSlice;
import com.aiplatform.foundation.modelruntime.domain.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionStatus;
import com.aiplatform.foundation.security.secret.SecretReference;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 PostgreSQL 事务锁实现租户内幂等和配额的供应商连接仓储。
 */
public final class PostgresProviderConnectionRepository
        implements ProviderConnectionRepository {

    private static final int MAX_CURSOR_LENGTH = 512;

    private final ProviderConnectionMapper mapper;
    private final TransactionTemplate transactions;

    public PostgresProviderConnectionRepository(
            ProviderConnectionMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "供应商连接映射器不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public ProviderConnectionCreationOutcome create(
            ProviderConnectionCreation creation,
            int tenantLimit) {
        Objects.requireNonNull(creation, "供应商连接创建请求不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户供应商连接配额必须大于零");
        }
        var result = transactions.execute(
                status -> createInTransaction(creation, tenantLimit));
        return Objects.requireNonNull(result, "供应商连接创建事务没有返回结果");
    }

    @Override
    public Optional<ProviderConnection> findById(
            TenantId tenantId,
            ProviderConnectionId connectionId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(connectionId, "供应商连接标识不能为空");
        return Optional.ofNullable(mapper.findById(tenantId.value(), connectionId.value()))
                .map(PostgresProviderConnectionRepository::toDomain);
    }

    @Override
    public ProviderConnectionSlice findPage(
            TenantId tenantId,
            String cursor,
            int limit) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("分页条数必须在 1 到 100 之间");
        }
        var fetchLimit = limit + 1;
        List<ProviderConnectionRow> rows;
        if (cursor == null) {
            rows = mapper.findFirstPage(tenantId.value(), fetchLimit);
        } else {
            var position = decodeCursor(cursor);
            rows = mapper.findAfter(
                    tenantId.value(),
                    position.createdAt(),
                    position.id(),
                    fetchLimit);
        }
        var hasNext = rows.size() > limit;
        var pageRows = hasNext ? rows.subList(0, limit) : rows;
        var items = pageRows.stream()
                .map(PostgresProviderConnectionRepository::toDomain)
                .toList();
        var nextCursor = hasNext
                ? encodeCursor(
                        pageRows.getLast().getCreatedAt(),
                        pageRows.getLast().getId())
                : null;
        return new ProviderConnectionSlice(items, nextCursor);
    }

    @Override
    public boolean update(ProviderConnection connection, long expectedVersion) {
        Objects.requireNonNull(connection, "供应商连接不能为空");
        if (connection.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("供应商连接新旧版本关系不正确");
        }
        return mapper.updateProbeFact(
                        connection.tenantId().value(),
                        connection.id().value(),
                        connection.status().name(),
                        connection.version(),
                        connection.lastProbeSucceeded(),
                        connection.lastProbeCode(),
                        connection.lastProbedAt(),
                        expectedVersion)
                == 1;
    }

    private ProviderConnectionCreationOutcome createInTransaction(
            ProviderConnectionCreation creation,
            int tenantLimit) {
        var connection = creation.connection();
        var tenantId = connection.tenantId().value();
        var keyHash = sha256(creation.idempotencyKey());
        mapper.lockTenant(Long.toString(tenantId));

        var existing = mapper.findRequest(tenantId, keyHash);
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(creation.requestFingerprint())) {
                throw new ProviderConnectionIdempotencyConflictException();
            }
            var replayed = mapper.findById(
                    tenantId,
                    existing.getProviderConnectionId());
            if (replayed == null) {
                throw new IllegalStateException("幂等记录引用的供应商连接不存在");
            }
            return new ProviderConnectionCreationOutcome(toDomain(replayed), true);
        }
        if (mapper.countActive(tenantId) >= tenantLimit) {
            throw new ProviderConnectionQuotaExceededException();
        }
        try {
            mapper.insertConnection(
                    tenantId,
                    connection.id().value(),
                    connection.providerKind().name(),
                    connection.displayName(),
                    connection.endpoint().toString(),
                    connection.region(),
                    connection.authenticationType().name(),
                    connection.credentialReference().value(),
                    connection.status().name(),
                    connection.version(),
                    connection.lastProbeSucceeded(),
                    connection.lastProbeCode(),
                    connection.lastProbedAt(),
                    connection.createdAt());
            mapper.insertRequest(
                    tenantId,
                    keyHash,
                    creation.requestFingerprint(),
                    connection.id().value(),
                    connection.createdAt());
        } catch (DuplicateKeyException exception) {
            throw new ProviderConnectionNameConflictException();
        }
        return new ProviderConnectionCreationOutcome(connection, false);
    }

    private static ProviderConnection toDomain(ProviderConnectionRow row) {
        return ProviderConnection.reconstitute(
                TenantId.parse(row.getTenantId()),
                new ProviderConnectionId(row.getId()),
                ProviderKind.valueOf(row.getProviderKind()),
                row.getDisplayName(),
                URI.create(row.getEndpoint()),
                row.getRegion(),
                AuthenticationType.valueOf(row.getAuthenticationType()),
                new SecretReference(row.getCredentialReference()),
                ProviderConnectionStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getLastProbeSucceeded(),
                row.getLastProbeCode(),
                row.getLastProbedAt(),
                row.getCreatedAt());
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static String encodeCursor(Instant createdAt, long id) {
        var payload = createdAt + "|" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static CursorPosition decodeCursor(String cursor) {
        if (cursor == null
                || cursor.isBlank()
                || cursor.length() > MAX_CURSOR_LENGTH
                || !cursor.equals(cursor.trim())) {
            throw new IllegalArgumentException("游标格式不正确");
        }
        try {
            var payload = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8);
            var separator = payload.lastIndexOf('|');
            if (separator < 1 || separator == payload.length() - 1) {
                throw new IllegalArgumentException("游标格式不正确");
            }
            return new CursorPosition(
                    Instant.parse(payload.substring(0, separator)),
                    Long.parseLong(payload.substring(separator + 1)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("游标格式不正确", exception);
        }
    }

    private record CursorPosition(Instant createdAt, long id) {
    }
}

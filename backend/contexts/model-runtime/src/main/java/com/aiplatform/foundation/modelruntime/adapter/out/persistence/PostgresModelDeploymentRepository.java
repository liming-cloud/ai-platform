package com.aiplatform.foundation.modelruntime.adapter.out.persistence;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.application.catalog.ModelDeploymentIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.ModelDeploymentNameConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.ModelDeploymentQuotaExceededException;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentCreationOutcome;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentSlice;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.api.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 PostgreSQL 事务锁实现租户内幂等和配额的模型部署仓储。
 */
public final class PostgresModelDeploymentRepository
        implements ModelDeploymentRepository {

    private static final int MAX_CURSOR_LENGTH = 512;

    private final ModelDeploymentMapper mapper;
    private final TransactionTemplate transactions;

    public PostgresModelDeploymentRepository(
            ModelDeploymentMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "模型部署映射器不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public ModelDeploymentCreationOutcome create(
            ModelDeploymentCreation creation,
            int tenantLimit) {
        Objects.requireNonNull(creation, "模型部署创建请求不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户模型部署配额必须大于零");
        }
        var result = transactions.execute(
                status -> createInTransaction(creation, tenantLimit));
        return Objects.requireNonNull(result, "模型部署创建事务没有返回结果");
    }

    @Override
    public Optional<ModelDeployment> findById(
            TenantId tenantId,
            ModelDeploymentId deploymentId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(deploymentId, "模型部署标识不能为空");
        return Optional.ofNullable(mapper.findById(tenantId.value(), deploymentId.value()))
                .map(PostgresModelDeploymentRepository::toDomain);
    }

    @Override
    public ModelDeploymentSlice findPage(
            TenantId tenantId,
            String cursor,
            int limit) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("分页条数必须在 1 到 100 之间");
        }
        var fetchLimit = limit + 1;
        List<ModelDeploymentRow> rows;
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
                .map(PostgresModelDeploymentRepository::toDomain)
                .toList();
        var nextCursor = hasNext
                ? encodeCursor(
                        pageRows.getLast().getCreatedAt(),
                        pageRows.getLast().getId())
                : null;
        return new ModelDeploymentSlice(items, nextCursor);
    }

    @Override
    public boolean update(ModelDeployment deployment, long expectedVersion) {
        Objects.requireNonNull(deployment, "模型部署不能为空");
        if (deployment.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("模型部署新旧版本关系不正确");
        }
        return mapper.updateStatus(
                        deployment.tenantId().value(),
                        deployment.id().value(),
                        deployment.status().name(),
                        deployment.version(),
                        expectedVersion)
                == 1;
    }

    private ModelDeploymentCreationOutcome createInTransaction(
            ModelDeploymentCreation creation,
            int tenantLimit) {
        var deployment = creation.deployment();
        var tenantId = deployment.tenantId().value();
        var keyHash = sha256(creation.idempotencyKey());
        mapper.lockTenant(Long.toString(tenantId));

        var existing = mapper.findRequest(tenantId, keyHash);
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(creation.requestFingerprint())) {
                throw new ModelDeploymentIdempotencyConflictException();
            }
            var replayed = mapper.findById(
                    tenantId,
                    existing.getModelDeploymentId());
            if (replayed == null) {
                throw new IllegalStateException("幂等记录引用的模型部署不存在");
            }
            return new ModelDeploymentCreationOutcome(toDomain(replayed), true);
        }
        if (mapper.countAll(tenantId) >= tenantLimit) {
            throw new ModelDeploymentQuotaExceededException();
        }
        try {
            mapper.insertDeployment(
                    tenantId,
                    deployment.id().value(),
                    deployment.providerConnectionId().value(),
                    deployment.providerConnectionRevision(),
                    deployment.modelCode(),
                    deployment.displayName(),
                    deployment.modelType().name(),
                    capabilities(deployment.capabilities()),
                    deployment.contextWindow(),
                    deployment.outputLimit(),
                    deployment.embeddingDimension(),
                    deployment.status().name(),
                    deployment.version(),
                    deployment.createdAt());
            mapper.insertRequest(
                    tenantId,
                    keyHash,
                    creation.requestFingerprint(),
                    deployment.id().value(),
                    deployment.createdAt());
        } catch (DuplicateKeyException exception) {
            throw new ModelDeploymentNameConflictException();
        }
        return new ModelDeploymentCreationOutcome(deployment, false);
    }

    private static ModelDeployment toDomain(ModelDeploymentRow row) {
        return ModelDeployment.reconstitute(
                TenantId.parse(row.getTenantId()),
                new ModelDeploymentId(row.getId()),
                new ProviderConnectionId(row.getProviderConnectionId()),
                row.getProviderConnectionRevision(),
                row.getModelCode(),
                row.getDisplayName(),
                ModelType.valueOf(row.getModelType()),
                parseCapabilities(row.getCapabilities()),
                row.getContextWindow(),
                row.getOutputLimit(),
                row.getEmbeddingDimension(),
                ModelDeploymentStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getCreatedAt());
    }

    private static String capabilities(Set<ModelCapability> values) {
        return values.stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    private static Set<ModelCapability> parseCapabilities(String value) {
        return Arrays.stream(value.split(",", -1))
                .map(ModelCapability::valueOf)
                .collect(Collectors.toUnmodifiableSet());
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

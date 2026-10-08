package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.repository;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.IdempotencyConflictException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgeSpaceNameConflictException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgeSpaceQuotaExceededException;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpaceCreation;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpaceCreationOutcome;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpacePage;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpace;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceStatus;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.mapper.KnowledgeSpaceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.mapper.KnowledgeSpaceRequestMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.entity.KnowledgeSpaceEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.entity.KnowledgeSpaceRequestEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 PostgreSQL 事务锁实现租户内幂等和配额的知识空间仓储。
 */
public final class PostgresKnowledgeSpaceRepository implements KnowledgeSpaceRepository {

    private final KnowledgeSpaceMapper mapper;
    private final KnowledgeSpaceRequestMapper requestMapper;
    private final TransactionTemplate transactions;

    public PostgresKnowledgeSpaceRepository(
            KnowledgeSpaceMapper mapper,
            KnowledgeSpaceRequestMapper requestMapper,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "知识空间映射器不能为空");
        this.requestMapper = Objects.requireNonNull(
                requestMapper,
                "知识空间幂等请求映射器不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public KnowledgeSpaceCreationOutcome create(
            KnowledgeSpaceCreation creation,
            int tenantLimit) {
        Objects.requireNonNull(creation, "创建请求不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户知识空间配额必须大于零");
        }
        var result = transactions.execute(status -> createInTransaction(creation, tenantLimit));
        return Objects.requireNonNull(result, "知识空间创建事务没有返回结果");
    }

    @Override
    public KnowledgeSpacePage findPage(
            TenantId tenantId,
            long pageNumber,
            long pageSize) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        var page = mapper.selectPage(
                new Page<KnowledgeSpaceEntity>(pageNumber, pageSize, true),
                new LambdaQueryWrapper<KnowledgeSpaceEntity>()
                        .eq(KnowledgeSpaceEntity::getTenantId, tenantId.value())
                        .ne(KnowledgeSpaceEntity::getStatus, "DELETED")
                        .orderByDesc(
                                KnowledgeSpaceEntity::getCreatedAt,
                                KnowledgeSpaceEntity::getId));
        return new KnowledgeSpacePage(
                page.getRecords().stream()
                        .map(PostgresKnowledgeSpaceRepository::toDomain)
                        .toList(),
                pageNumber,
                pageSize,
                page.getTotal());
    }

    @Override
    public boolean exists(TenantId tenantId, KnowledgeSpaceId knowledgeSpaceId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        return mapper.selectCount(
                new LambdaQueryWrapper<KnowledgeSpaceEntity>()
                        .eq(KnowledgeSpaceEntity::getTenantId, tenantId.value())
                        .eq(KnowledgeSpaceEntity::getId, knowledgeSpaceId.value())
                        .ne(KnowledgeSpaceEntity::getStatus, "DELETED"))
                > 0;
    }

    @Override
    public java.util.Optional<String> findName(TenantId tenantId, KnowledgeSpaceId knowledgeSpaceId) {
        return java.util.Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<KnowledgeSpaceEntity>()
                .select(KnowledgeSpaceEntity::getName)
                .eq(KnowledgeSpaceEntity::getTenantId, tenantId.value())
                .eq(KnowledgeSpaceEntity::getId, knowledgeSpaceId.value())
                .ne(KnowledgeSpaceEntity::getStatus, "DELETED"))).map(KnowledgeSpaceEntity::getName);
    }

    private KnowledgeSpaceCreationOutcome createInTransaction(
            KnowledgeSpaceCreation creation,
            int tenantLimit) {
        var space = creation.space();
        var tenantId = space.tenantId().value();
        var keyHash = sha256(creation.idempotencyKey());
        mapper.lockTenant(Long.toString(tenantId));

        var existing = requestMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeSpaceRequestEntity>()
                        .eq(KnowledgeSpaceRequestEntity::getTenantId, tenantId)
                        .eq(KnowledgeSpaceRequestEntity::getIdempotencyKeyHash, keyHash));
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(creation.requestFingerprint())) {
                throw new IdempotencyConflictException();
            }
            var replayed = mapper.selectOne(
                    new LambdaQueryWrapper<KnowledgeSpaceEntity>()
                            .eq(KnowledgeSpaceEntity::getTenantId, tenantId)
                            .eq(
                                    KnowledgeSpaceEntity::getId,
                                    existing.getKnowledgeSpaceId()));
            if (replayed == null) {
                throw new IllegalStateException("幂等记录引用的知识空间不存在");
            }
            return new KnowledgeSpaceCreationOutcome(toDomain(replayed), true);
        }

        if (mapper.selectCount(
                new LambdaQueryWrapper<KnowledgeSpaceEntity>()
                        .eq(KnowledgeSpaceEntity::getTenantId, tenantId)
                        .ne(KnowledgeSpaceEntity::getStatus, "DELETED"))
                >= tenantLimit) {
            throw new KnowledgeSpaceQuotaExceededException();
        }
        try {
            mapper.insert(toEntity(space));
            requestMapper.insert(toRequestEntity(creation, keyHash));
        } catch (DuplicateKeyException exception) {
            throw new KnowledgeSpaceNameConflictException();
        }
        return new KnowledgeSpaceCreationOutcome(space, false);
    }

    private static KnowledgeSpace toDomain(KnowledgeSpaceEntity row) {
        return KnowledgeSpace.reconstitute(
                new TenantId(row.getTenantId()),
                new KnowledgeSpaceId(row.getId()),
                row.getName(),
                row.getDescription(),
                KnowledgeSpaceStatus.valueOf(row.getStatus()),
                row.getVersion(),
                new CreatorId(row.getCreatedBy()),
                row.getCreatedAt());
    }

    private static KnowledgeSpaceEntity toEntity(KnowledgeSpace space) {
        var entity = new KnowledgeSpaceEntity();
        entity.setId(space.id().value());
        entity.setTenantId(space.tenantId().value());
        entity.setName(space.name());
        entity.setDescription(space.description());
        entity.setStatus(space.status().name());
        entity.setVersion(space.version());
        entity.setCreatedBy(space.createdBy().value());
        entity.setCreatedAt(space.createdAt());
        return entity;
    }

    private static KnowledgeSpaceRequestEntity toRequestEntity(
            KnowledgeSpaceCreation creation,
            String keyHash) {
        var entity = new KnowledgeSpaceRequestEntity();
        entity.setId(creation.space().id().value());
        entity.setTenantId(creation.space().tenantId().value());
        entity.setIdempotencyKeyHash(keyHash);
        entity.setRequestFingerprint(creation.requestFingerprint());
        entity.setKnowledgeSpaceId(creation.space().id().value());
        entity.setCreatedAt(creation.space().createdAt());
        return entity;
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

}

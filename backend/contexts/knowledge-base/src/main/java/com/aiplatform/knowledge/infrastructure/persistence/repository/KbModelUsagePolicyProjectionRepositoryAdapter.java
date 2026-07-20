package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.knowledge.domain.modelpolicy.KnowledgeModelUsagePolicyProjection;
import com.aiplatform.knowledge.domain.port.modelpolicy.KnowledgeModelUsagePolicyProjectionRepository;
import com.aiplatform.knowledge.infrastructure.persistence.converter.KbModelUsagePolicyProjectionPersistenceConverter;
import com.aiplatform.knowledge.infrastructure.persistence.entity.KbModelUsagePolicyProjectionPersistenceEntity;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.KbModelUsagePolicyProjectionMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 模型使用策略投影 MyBatis-Plus 仓储适配器。
 */
@Repository
public class KbModelUsagePolicyProjectionRepositoryAdapter
        implements KnowledgeModelUsagePolicyProjectionRepository {
    private final KbModelUsagePolicyProjectionMapper mapper;
    private final KbModelUsagePolicyProjectionPersistenceConverter converter;

    public KbModelUsagePolicyProjectionRepositoryAdapter(
            KbModelUsagePolicyProjectionMapper mapper,
            KbModelUsagePolicyProjectionPersistenceConverter converter
    ) {
        this.mapper = Objects.requireNonNull(mapper, "模型策略投影 Mapper 不能为空");
        this.converter = Objects.requireNonNull(converter, "模型策略投影转换器不能为空");
    }

    @Override
    public Optional<KnowledgeModelUsagePolicyProjection> findByModelConfigId(
            TenantId tenantId,
            String modelConfigId
    ) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        KbModelUsagePolicyProjectionPersistenceEntity row = mapper.selectOne(baseQuery(tenantId, modelConfigId)
                .last("limit 1"));
        return Optional.ofNullable(row).map(converter::toDomain);
    }

    @Override
    public KnowledgeModelUsagePolicyProjection save(KnowledgeModelUsagePolicyProjection projection) {
        Objects.requireNonNull(projection, "模型策略投影不能为空");
        KbModelUsagePolicyProjectionPersistenceEntity existing = mapper.selectOne(baseQuery(
                projection.tenantId(),
                projection.modelConfigId()
        ).last("limit 1"));
        if (supersedes(existing, projection)) {
            return converter.toDomain(existing);
        }
        KbModelUsagePolicyProjectionPersistenceEntity entity = converter.toEntity(
                projection,
                existing == null ? null : existing.getId(),
                existing == null ? null : existing.getVersion()
        );
        if (existing != null) {
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setVersion(requiredInteger(existing.getVersion(), "模型策略投影乐观锁版本") + 1);
        }
        if (existing == null) {
            requiredInsertedRows(mapper.insert(entity), "模型策略投影");
        } else {
            int updated = mapper.update(entity, activeProjectionUpdate(entity, existing.getVersion()));
            if (updated != 1) {
                return resolveConcurrentUpdate(projection);
            }
        }
        return converter.toDomain(entity);
    }

    private LambdaUpdateWrapper<KbModelUsagePolicyProjectionPersistenceEntity> activeProjectionUpdate(
            KbModelUsagePolicyProjectionPersistenceEntity entity,
            Integer currentVersion
    ) {
        return new LambdaUpdateWrapper<KbModelUsagePolicyProjectionPersistenceEntity>()
                .eq(KbModelUsagePolicyProjectionPersistenceEntity::getTenantId, entity.getTenantId())
                .eq(KbModelUsagePolicyProjectionPersistenceEntity::getModelConfigId, entity.getModelConfigId())
                .eq(KbModelUsagePolicyProjectionPersistenceEntity::getId, requiredLong(entity.getId(), "模型策略投影 ID"))
                .eq(KbModelUsagePolicyProjectionPersistenceEntity::getVersion,
                        requiredInteger(currentVersion, "模型策略投影乐观锁版本"))
                .isNull(KbModelUsagePolicyProjectionPersistenceEntity::getDeletedAt);
    }

    private KnowledgeModelUsagePolicyProjection resolveConcurrentUpdate(
            KnowledgeModelUsagePolicyProjection projection
    ) {
        return findByModelConfigId(projection.tenantId(), projection.modelConfigId())
                .filter(current -> supersedes(current.policyRevision(), current.policyDigest(), projection))
                .orElseThrow(() -> new IllegalStateException("模型策略投影保存失败：并发更新未产生更新策略"));
    }

    private static boolean supersedes(
            KbModelUsagePolicyProjectionPersistenceEntity existing,
            KnowledgeModelUsagePolicyProjection incoming
    ) {
        return existing != null && supersedes(
                requiredPolicyRevision(existing.getPolicyRevision()),
                requiredPolicyDigest(existing.getPolicyDigest()),
                incoming
        );
    }

    private static boolean supersedes(
            long currentRevision,
            String currentDigest,
            KnowledgeModelUsagePolicyProjection incoming
    ) {
        if (currentRevision > incoming.policyRevision()) {
            return true;
        }
        if (currentRevision < incoming.policyRevision()) {
            return false;
        }
        if (!currentDigest.equals(incoming.policyDigest())) {
            throw new IllegalStateException("知识库模型策略同一修订号载荷摘要冲突");
        }
        return true;
    }

    private static long requiredPolicyRevision(Long revision) {
        if (revision == null || revision <= 0) {
            throw new IllegalStateException("知识库模型策略投影修订号非法");
        }
        return revision;
    }

    private static String requiredPolicyDigest(String digest) {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("知识库模型策略投影摘要非法");
        }
        return digest;
    }

    private LambdaQueryWrapper<KbModelUsagePolicyProjectionPersistenceEntity> baseQuery(
            TenantId tenantId,
            String modelConfigId
    ) {
        return new LambdaQueryWrapper<KbModelUsagePolicyProjectionPersistenceEntity>()
                .eq(KbModelUsagePolicyProjectionPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(KbModelUsagePolicyProjectionPersistenceEntity::getModelConfigId, modelConfigId)
                .isNull(KbModelUsagePolicyProjectionPersistenceEntity::getDeletedAt);
    }

    private static Long requiredLong(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value;
    }

    private static int requiredInteger(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value;
    }

    private static void requiredInsertedRows(int inserted, String fieldName) {
        if (inserted != 1) {
            throw new IllegalStateException(fieldName + "保存失败：数据库未写入记录");
        }
    }
}

package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseListItemProjection;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.infrastructure.persistence.entity.KbKnowledgeBasePersistenceEntity;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.KbKnowledgeBaseMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 知识库仓储 MyBatis-Plus 适配器，所有查询必须携带租户与逻辑删除条件。
 */
@Repository
public class KbKnowledgeBaseRepositoryAdapter implements KnowledgeBaseRepository {
    private final KbKnowledgeBaseMapper mapper;

    public KbKnowledgeBaseRepositoryAdapter(KbKnowledgeBaseMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "知识库 Mapper 不能为空");
    }

    @Override
    public String nextKnowledgeBaseId() {
        return requiredGeneratedId(mapper.nextKnowledgeBaseId(), "知识库 ID");
    }

    @Override
    public KnowledgeBase save(KnowledgeBase knowledgeBase) {
        KbKnowledgeBasePersistenceEntity entity = KbKnowledgeBasePersistenceEntity.fromDomain(knowledgeBase);
        if (findActiveRow(entity).isEmpty()) {
            requiredInsertedRows(mapper.insert(entity), "知识库");
        } else {
            int updated = mapper.update(entity, activeRowUpdate(entity));
            if (updated != 1) {
                throw new IllegalStateException("知识库保存失败：记录不存在或已被删除");
            }
        }
        return entity.toDomain();
    }

    @Override
    public Optional<KnowledgeBase> findById(TenantId tenantId, String knowledgeBaseId) {
        KbKnowledgeBasePersistenceEntity row = mapper.selectOne(new LambdaQueryWrapper<KbKnowledgeBasePersistenceEntity>()
                .eq(KbKnowledgeBasePersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(KbKnowledgeBasePersistenceEntity::getId, parseRequiredLong(knowledgeBaseId, "知识库 ID"))
                .isNull(KbKnowledgeBasePersistenceEntity::getDeletedAt)
                .last("limit 1"));
        return Optional.ofNullable(row).map(KbKnowledgeBasePersistenceEntity::toDomain);
    }

    @Override
    public boolean existsById(TenantId tenantId, String knowledgeBaseId) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        return mapper.selectCount(new LambdaQueryWrapper<KbKnowledgeBasePersistenceEntity>()
                .eq(KbKnowledgeBasePersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(KbKnowledgeBasePersistenceEntity::getId, parseRequiredLong(knowledgeBaseId, "知识库 ID"))
                .isNull(KbKnowledgeBasePersistenceEntity::getDeletedAt)) > 0;
    }

    @Override
    public List<KnowledgeBaseListItemProjection> findListItemsByTenant(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        return mapper.selectList(new LambdaQueryWrapper<KbKnowledgeBasePersistenceEntity>()
                        .eq(KbKnowledgeBasePersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                        .isNull(KbKnowledgeBasePersistenceEntity::getDeletedAt)
                        .orderByDesc(KbKnowledgeBasePersistenceEntity::getUpdatedAt))
                .stream()
                .map(KbKnowledgeBaseRepositoryAdapter::toListItemProjection)
                .toList();
    }

    private Optional<KbKnowledgeBasePersistenceEntity> findActiveRow(KbKnowledgeBasePersistenceEntity entity) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<KbKnowledgeBasePersistenceEntity>()
                .eq(KbKnowledgeBasePersistenceEntity::getTenantId, entity.getTenantId())
                .eq(KbKnowledgeBasePersistenceEntity::getId, entity.getId())
                .isNull(KbKnowledgeBasePersistenceEntity::getDeletedAt)
                .last("limit 1")));
    }

    private LambdaUpdateWrapper<KbKnowledgeBasePersistenceEntity> activeRowUpdate(KbKnowledgeBasePersistenceEntity entity) {
        return new LambdaUpdateWrapper<KbKnowledgeBasePersistenceEntity>()
                .eq(KbKnowledgeBasePersistenceEntity::getTenantId, entity.getTenantId())
                .eq(KbKnowledgeBasePersistenceEntity::getId, entity.getId())
                .isNull(KbKnowledgeBasePersistenceEntity::getDeletedAt);
    }

    private static String requiredGeneratedId(Long id, String fieldName) {
        if (id == null) {
            throw new IllegalStateException(fieldName + "生成失败：数据库序列未返回主键");
        }
        return String.valueOf(id);
    }

    private static void requiredInsertedRows(int inserted, String fieldName) {
        if (inserted != 1) {
            throw new IllegalStateException(fieldName + "保存失败：数据库未写入记录");
        }
    }

    private static KnowledgeBaseListItemProjection toListItemProjection(KbKnowledgeBasePersistenceEntity row) {
        return new KnowledgeBaseListItemProjection(
                requiredLongText(row.getId(), "知识库 ID"),
                requiredText(row.getKbName(), "知识库名称"),
                requiredText(row.getVisibility(), "知识库可见性"),
                requiredText(row.getStatus(), "知识库状态"),
                optionalLongText(row.getEmbeddingModelConfigId())
        );
    }

    private static String requiredLongText(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("知识库持久化记录缺少" + fieldName);
        }
        return String.valueOf(value);
    }

    private static String optionalLongText(Long value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("知识库持久化记录缺少" + fieldName);
        }
        return value.trim();
    }
}

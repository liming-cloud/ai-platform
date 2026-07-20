package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.infrastructure.persistence.entity.KbDocumentPersistenceEntity;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.KbDocumentMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 知识文档仓储 MyBatis-Plus 适配器，查询必须收敛到租户和知识库。
 */
@Repository
public class KbDocumentRepositoryAdapter implements KnowledgeDocumentRepository {
    private final KbDocumentMapper mapper;

    public KbDocumentRepositoryAdapter(KbDocumentMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "知识文档 Mapper 不能为空");
    }

    @Override
    public String nextDocumentId() {
        return requiredGeneratedId(mapper.nextDocumentId(), "知识文档 ID");
    }

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        KbDocumentPersistenceEntity entity = KbDocumentPersistenceEntity.fromDomain(document);
        if (findActiveRow(entity).isEmpty()) {
            requiredInsertedRows(mapper.insert(entity), "知识文档");
        } else {
            int updated = mapper.update(entity, activeRowUpdate(entity));
            if (updated != 1) {
                throw new IllegalStateException("知识文档保存失败：记录不存在或已被删除");
            }
        }
        return entity.toDomain();
    }

    @Override
    public Optional<KnowledgeDocument> findById(TenantId tenantId, String knowledgeBaseId, String documentId) {
        KbDocumentPersistenceEntity row = mapper.selectOne(new LambdaQueryWrapper<KbDocumentPersistenceEntity>()
                .eq(KbDocumentPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(KbDocumentPersistenceEntity::getKnowledgeBaseId, parseRequiredLong(knowledgeBaseId, "知识库 ID"))
                .eq(KbDocumentPersistenceEntity::getId, parseRequiredLong(documentId, "文档 ID"))
                .isNull(KbDocumentPersistenceEntity::getDeletedAt)
                .last("limit 1"));
        return Optional.ofNullable(row).map(KbDocumentPersistenceEntity::toDomain);
    }

    @Override
    public void requireForUpdate(TenantId tenantId, String knowledgeBaseId, String documentId) {
        long expectedDocumentId = parseRequiredLong(documentId, "文档 ID");
        Long locked = mapper.lockById(
                parseRequiredLong(tenantId.value(), "租户 ID"),
                parseRequiredLong(knowledgeBaseId, "知识库 ID"),
                expectedDocumentId
        );
        if (locked == null || locked != expectedDocumentId) {
            throw new IllegalStateException("知识文档不存在或已删除，无法锁定");
        }
    }

    @Override
    public List<KnowledgeDocument> findByKnowledgeBaseId(TenantId tenantId, String knowledgeBaseId) {
        List<KbDocumentPersistenceEntity> rows = mapper.selectList(new LambdaQueryWrapper<KbDocumentPersistenceEntity>()
                .eq(KbDocumentPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(KbDocumentPersistenceEntity::getKnowledgeBaseId, parseRequiredLong(knowledgeBaseId, "知识库 ID"))
                .isNull(KbDocumentPersistenceEntity::getDeletedAt));
        return requiredRows(rows, "知识文档查询结果").stream().map(KbDocumentPersistenceEntity::toDomain).toList();
    }

    @Override
    public void cancel(
            TenantId tenantId,
            String knowledgeBaseId,
            String documentId,
            String operatorId,
            Instant cancelledAt
    ) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        Objects.requireNonNull(cancelledAt, "取消时间不能为空");
        int updated = mapper.update(null, new LambdaUpdateWrapper<>(KbDocumentPersistenceEntity.class)
                .eq(KbDocumentPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(KbDocumentPersistenceEntity::getKnowledgeBaseId, parseRequiredLong(knowledgeBaseId, "知识库 ID"))
                .eq(KbDocumentPersistenceEntity::getId, parseRequiredLong(documentId, "文档 ID"))
                .in(KbDocumentPersistenceEntity::getParseStatus, "UPLOADED", "PARSING", "CHUNKED", "VECTORIZING")
                .isNull(KbDocumentPersistenceEntity::getDeletedAt)
                .set(KbDocumentPersistenceEntity::getParseStatus, "CANCELLED")
                .set(KbDocumentPersistenceEntity::getFailureReason, null)
                .set(KbDocumentPersistenceEntity::getUpdatedAt, cancelledAt)
                .set(KbDocumentPersistenceEntity::getUpdatedBy, parseRequiredLong(operatorId, "操作人 ID"))
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new IllegalStateException("只有非终态知识文档才能取消");
        }
    }

    @Override
    public void remove(TenantId tenantId, String knowledgeBaseId, String documentId, String operatorId, Instant removedAt) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        Objects.requireNonNull(removedAt, "移除时间不能为空");
        int updated = mapper.update(null, new LambdaUpdateWrapper<>(KbDocumentPersistenceEntity.class)
                .eq(KbDocumentPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(KbDocumentPersistenceEntity::getKnowledgeBaseId, parseRequiredLong(knowledgeBaseId, "知识库 ID"))
                .eq(KbDocumentPersistenceEntity::getId, parseRequiredLong(documentId, "文档 ID"))
                .isNull(KbDocumentPersistenceEntity::getDeletedAt)
                .set(KbDocumentPersistenceEntity::getDeletedAt, removedAt)
                .set(KbDocumentPersistenceEntity::getUpdatedAt, removedAt)
                .set(KbDocumentPersistenceEntity::getUpdatedBy, parseRequiredLong(operatorId, "操作人 ID")));
        if (updated != 1) {
            throw new IllegalStateException("知识文档移除失败：记录不存在或已被删除");
        }
    }

    private Optional<KbDocumentPersistenceEntity> findActiveRow(KbDocumentPersistenceEntity entity) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<KbDocumentPersistenceEntity>()
                .eq(KbDocumentPersistenceEntity::getTenantId, entity.getTenantId())
                .eq(KbDocumentPersistenceEntity::getKnowledgeBaseId, entity.getKnowledgeBaseId())
                .eq(KbDocumentPersistenceEntity::getId, entity.getId())
                .isNull(KbDocumentPersistenceEntity::getDeletedAt)
                .last("limit 1")));
    }

    private LambdaUpdateWrapper<KbDocumentPersistenceEntity> activeRowUpdate(KbDocumentPersistenceEntity entity) {
        return new LambdaUpdateWrapper<KbDocumentPersistenceEntity>()
                .eq(KbDocumentPersistenceEntity::getTenantId, entity.getTenantId())
                .eq(KbDocumentPersistenceEntity::getKnowledgeBaseId, entity.getKnowledgeBaseId())
                .eq(KbDocumentPersistenceEntity::getId, entity.getId())
                .isNull(KbDocumentPersistenceEntity::getDeletedAt);
    }

    private static String requiredGeneratedId(Long id, String fieldName) {
        if (id == null) {
            throw new IllegalStateException(fieldName + "生成失败：数据库序列未返回主键");
        }
        return String.valueOf(id);
    }

    private static <T> List<T> requiredRows(List<T> rows, String fieldName) {
        if (rows == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return rows;
    }

    private static void requiredInsertedRows(int inserted, String fieldName) {
        if (inserted != 1) {
            throw new IllegalStateException(fieldName + "保存失败：数据库未写入记录");
        }
    }
}

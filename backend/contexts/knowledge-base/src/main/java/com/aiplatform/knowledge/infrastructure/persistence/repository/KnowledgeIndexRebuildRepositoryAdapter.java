package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildMember;
import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildPlan;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildStatus;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeIndexRebuildRepository;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.KnowledgeIndexRebuildMapper;
import com.aiplatform.knowledge.infrastructure.persistence.projection.KnowledgeIndexRebuildRow;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeIndexRebuildRepositoryAdapter implements KnowledgeIndexRebuildRepository {
    private final KnowledgeIndexRebuildMapper mapper;

    public KnowledgeIndexRebuildRepositoryAdapter(KnowledgeIndexRebuildMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "索引重建 Mapper 不能为空");
    }

    @Override
    public void lockKnowledgeBase(TenantId tenantId, String knowledgeBaseId) {
        Long locked = mapper.lockKnowledgeBase(tenantId(tenantId), id(knowledgeBaseId, "知识库 ID"));
        if (locked == null) {
            throw new IllegalStateException("知识库不存在或已删除，无法创建索引 generation");
        }
    }

    @Override
    public String nextRebuildId() {
        Long value = mapper.nextRebuildId();
        if (value == null) {
            throw new IllegalStateException("索引重建计划 ID 生成失败");
        }
        return String.valueOf(value);
    }

    @Override
    public long nextGeneration(TenantId tenantId, String knowledgeBaseId) {
        Long value = mapper.nextGeneration(tenantId(tenantId), id(knowledgeBaseId, "知识库 ID"));
        if (value == null || value < 1) {
            throw new IllegalStateException("索引 generation 生成失败");
        }
        return value;
    }

    @Override
    public Optional<KnowledgeIndexRebuildPlan> findActive(TenantId tenantId, String knowledgeBaseId) {
        return Optional.ofNullable(mapper.findActive(tenantId(tenantId), id(knowledgeBaseId, "知识库 ID")))
                .map(KnowledgeIndexRebuildRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<KnowledgeIndexRebuildPlan> findById(TenantId tenantId, String rebuildId) {
        return Optional.ofNullable(mapper.findById(
                        tenantId(tenantId), id(rebuildId, "重建计划 ID")))
                .map(KnowledgeIndexRebuildRepositoryAdapter::toDomain);
    }

    @Override
    public void failActive(
            KnowledgeIndexRebuildPlan plan,
            String failureCode,
            String failureReason,
            Instant now,
            String operatorId
    ) {
        requireOne(mapper.failActive(
                tenantId(plan.tenantId()),
                id(plan.knowledgeBaseId(), "知识库 ID"),
                id(plan.rebuildId(), "重建计划 ID"),
                requireText(failureCode, "失败码"),
                requireText(failureReason, "失败原因"),
                Objects.requireNonNull(now, "失败时间不能为空"),
                id(operatorId, "操作人 ID")
        ), "终止旧索引 generation");
    }

    @Override
    public void insertPlan(KnowledgeIndexRebuildPlan plan) {
        requireOne(mapper.insertPlan(toRow(Objects.requireNonNull(plan, "重建计划不能为空"))), "创建索引重建计划");
    }

    @Override
    public void insertMember(KnowledgeIndexRebuildPlan plan, KnowledgeIndexRebuildMember member) {
        Objects.requireNonNull(plan, "重建计划不能为空");
        Objects.requireNonNull(member, "重建成员不能为空");
        int inserted = mapper.insertMember(
                tenantId(plan.tenantId()),
                id(plan.rebuildId(), "重建计划 ID"),
                id(plan.knowledgeBaseId(), "知识库 ID"),
                id(member.documentId(), "文档 ID"),
                member.documentUpdatedAt(),
                member.expectedChunkCount()
        );
        if (inserted != 0 && inserted != 1) {
            throw new IllegalStateException("索引重建成员保存结果非法");
        }
    }

    @Override
    public void pointDesired(KnowledgeIndexRebuildPlan plan, boolean nextGenerationRequired) {
        requireOne(mapper.pointDesired(
                tenantId(plan.tenantId()),
                id(plan.knowledgeBaseId(), "知识库 ID"),
                id(plan.rebuildId(), "重建计划 ID"),
                plan.generation(),
                nextGenerationRequired,
                id(plan.operatorId(), "操作人 ID"),
                plan.createdAt()
        ), "更新 desired generation");
    }

    @Override
    public void markNextGenerationRequired(
            TenantId tenantId,
            String knowledgeBaseId,
            String operatorId,
            Instant now
    ) {
        int updated = mapper.markNextGenerationRequired(
                tenantId(tenantId),
                id(knowledgeBaseId, "知识库 ID"),
                id(operatorId, "操作人 ID"),
                Objects.requireNonNull(now, "脏标记时间不能为空")
        );
        if (updated < 0 || updated > 1) {
            throw new IllegalStateException("下一 generation 标记结果非法");
        }
    }

    @Override
    public void requireLatestActive(KnowledgeIndexRebuildPlan plan) {
        Objects.requireNonNull(plan, "重建计划不能为空");
        Long locked = mapper.lockLatestActive(
                tenantId(plan.tenantId()),
                id(plan.knowledgeBaseId(), "知识库 ID"),
                id(plan.rebuildId(), "重建计划 ID"),
                plan.generation()
        );
        if (locked == null) {
            throw new IllegalStateException("索引重建计划已不是 desired/latest ACTIVE generation");
        }
    }

    @Override
    public void lockFrozenMembers(KnowledgeIndexRebuildPlan plan) {
        Objects.requireNonNull(plan, "重建计划不能为空");
        List<Long> locked = mapper.lockFrozenMembers(
                tenantId(plan.tenantId()),
                id(plan.rebuildId(), "重建计划 ID"),
                id(plan.knowledgeBaseId(), "知识库 ID")
        );
        if (locked == null || locked.isEmpty()) {
            throw new IllegalStateException("索引重建计划没有可锁定的冻结成员");
        }
    }

    @Override
    public void requireCurrentMember(KnowledgeIndexRebuildPlan plan, KnowledgeDocument document) {
        Objects.requireNonNull(plan, "重建计划不能为空");
        Objects.requireNonNull(document, "冻结成员文档不能为空");
        Long locked = mapper.lockCurrentMember(
                tenantId(plan.tenantId()),
                id(plan.rebuildId(), "重建计划 ID"),
                id(plan.knowledgeBaseId(), "知识库 ID"),
                id(document.documentId(), "文档 ID"),
                document.updatedAt()
        );
        if (locked == null) {
            throw new IllegalStateException("索引重建冻结成员或切片集合已变化");
        }
    }

    @Override
    public boolean activateLatestIfComplete(KnowledgeIndexRebuildPlan plan, Instant now, String operatorId) {
        Long activated = mapper.activateLatestIfComplete(
                tenantId(plan.tenantId()),
                id(plan.knowledgeBaseId(), "知识库 ID"),
                id(plan.rebuildId(), "重建计划 ID"),
                plan.generation(),
                Objects.requireNonNull(now, "激活时间不能为空"),
                id(operatorId, "操作人 ID")
        );
        return activated != null && activated == id(plan.rebuildId(), "重建计划 ID");
    }

    private static KnowledgeIndexRebuildPlan toDomain(KnowledgeIndexRebuildRow row) {
        return new KnowledgeIndexRebuildPlan(
                TenantId.of(required(row.getTenantId(), "租户 ID")),
                required(row.getId(), "重建计划 ID"),
                required(row.getKnowledgeBaseId(), "知识库 ID"),
                requiredLong(row.getGeneration(), "generation"),
                required(row.getTargetModelConfigId(), "模型配置 ID"),
                requireText(row.getTargetEmbeddingModel(), "目标 Embedding 模型"),
                requireText(row.getIndexVersion(), "索引版本"),
                KnowledgeIndexRebuildStatus.from(requireText(row.getStatus(), "重建状态")),
                Objects.requireNonNull(row.getMembersFrozenAt(), "成员冻结时间不能为空"),
                Objects.requireNonNull(row.getCreatedAt(), "计划创建时间不能为空"),
                required(row.getCreatedBy(), "操作人 ID")
        );
    }

    private static KnowledgeIndexRebuildRow toRow(KnowledgeIndexRebuildPlan plan) {
        KnowledgeIndexRebuildRow row = new KnowledgeIndexRebuildRow();
        row.setId(id(plan.rebuildId(), "重建计划 ID"));
        row.setTenantId(tenantId(plan.tenantId()));
        row.setKnowledgeBaseId(id(plan.knowledgeBaseId(), "知识库 ID"));
        row.setGeneration(plan.generation());
        row.setTargetModelConfigId(id(plan.targetModelConfigId(), "模型配置 ID"));
        row.setTargetEmbeddingModel(plan.targetEmbeddingModel());
        row.setIndexVersion(plan.indexVersion());
        row.setStatus(plan.status().name());
        row.setMembersFrozenAt(plan.membersFrozenAt());
        row.setCreatedAt(plan.createdAt());
        row.setCreatedBy(id(plan.operatorId(), "操作人 ID"));
        return row;
    }

    private static long tenantId(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        return id(tenantId.value(), "租户 ID");
    }

    private static long id(String value, String fieldName) {
        return parseRequiredLong(value, fieldName);
    }

    private static String required(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("索引重建持久化记录缺少" + fieldName);
        }
        return String.valueOf(value);
    }

    private static long requiredLong(Long value, String fieldName) {
        if (value == null || value < 1) {
            throw new IllegalStateException("索引重建持久化记录缺少" + fieldName);
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static void requireOne(int rows, String action) {
        if (rows != 1) {
            throw new IllegalStateException(action + "失败：持久化未影响唯一记录");
        }
    }
}

package com.aiplatform.knowledge.domain.port.indexing;

import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildMember;
import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildPlan;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Optional;

/**
 * 冻结重建计划与 desired/latest generation 的持久化 seam。
 */
public interface KnowledgeIndexRebuildRepository {
    void lockKnowledgeBase(TenantId tenantId, String knowledgeBaseId);

    String nextRebuildId();

    long nextGeneration(TenantId tenantId, String knowledgeBaseId);

    Optional<KnowledgeIndexRebuildPlan> findActive(TenantId tenantId, String knowledgeBaseId);

    Optional<KnowledgeIndexRebuildPlan> findById(TenantId tenantId, String rebuildId);

    default void failActive(
            KnowledgeIndexRebuildPlan plan,
            String failureCode,
            String failureReason,
            Instant now
    ) {
        failActive(plan, failureCode, failureReason, now, plan.operatorId());
    }

    void failActive(
            KnowledgeIndexRebuildPlan plan,
            String failureCode,
            String failureReason,
            Instant now,
            String operatorId
    );

    void insertPlan(KnowledgeIndexRebuildPlan plan);

    void insertMember(KnowledgeIndexRebuildPlan plan, KnowledgeIndexRebuildMember member);

    void pointDesired(KnowledgeIndexRebuildPlan plan, boolean nextGenerationRequired);

    void markNextGenerationRequired(TenantId tenantId, String knowledgeBaseId, String operatorId, Instant now);

    void requireLatestActive(KnowledgeIndexRebuildPlan plan);

    /**
     * 以稳定文档 ID 顺序锁定冻结 cohort 的成员及文档，阻止激活与重解析交错。
     */
    void lockFrozenMembers(KnowledgeIndexRebuildPlan plan);

    void requireCurrentMember(KnowledgeIndexRebuildPlan plan, KnowledgeDocument document);

    boolean activateLatestIfComplete(KnowledgeIndexRebuildPlan plan, Instant now, String operatorId);
}

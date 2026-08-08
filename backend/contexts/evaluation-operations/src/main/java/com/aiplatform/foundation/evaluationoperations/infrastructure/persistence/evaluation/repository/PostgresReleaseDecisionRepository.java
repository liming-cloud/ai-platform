package com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationTarget;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecision;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecisionId;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecisionReasonCode;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecisionStatus;
import com.aiplatform.foundation.evaluationoperations.domain.release.repository.ReleaseDecisionRepository;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.entity.ReleaseDecisionEntity;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.mapper.ReleaseDecisionMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Objects;
import java.util.Optional;

/**
 * 原样保存与恢复发布决定，不重新解释质量门禁或修改结论。
 */
public final class PostgresReleaseDecisionRepository implements ReleaseDecisionRepository {

    private final ReleaseDecisionMapper mapper;

    public PostgresReleaseDecisionRepository(ReleaseDecisionMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "发布决定映射器不能为空");
    }

    @Override
    public void save(ReleaseDecision decision) {
        decision = Objects.requireNonNull(decision, "发布决定不能为空");
        if (mapper.insert(toEntity(decision)) != 1) {
            throw new IllegalStateException("发布决定提交失败");
        }
    }

    @Override
    public Optional<ReleaseDecision> findById(TenantId tenantId, ReleaseDecisionId id) {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        id = Objects.requireNonNull(id, "发布决定标识不能为空");
        var entity = mapper.selectOne(new LambdaQueryWrapper<ReleaseDecisionEntity>()
                .eq(ReleaseDecisionEntity::getTenantId, tenantId.value())
                .eq(ReleaseDecisionEntity::getId, id.value()));
        return Optional.ofNullable(entity).map(PostgresReleaseDecisionRepository::toDomain);
    }

    private static ReleaseDecisionEntity toEntity(ReleaseDecision decision) {
        var entity = new ReleaseDecisionEntity();
        entity.setId(decision.id().value());
        entity.setTenantId(decision.tenantId().value());
        entity.setEvaluationRunId(decision.evaluationRunId().value());
        entity.setAssistantVersionId(decision.target().assistantVersionId());
        entity.setAssistantVersionRevision(decision.target().assistantVersionRevision());
        entity.setPolicyVersion(decision.policyVersion());
        entity.setStatus(decision.status().name());
        entity.setReasonCode(decision.reasonCode().name());
        entity.setVersion(decision.version());
        entity.setDecidedAt(decision.decidedAt());
        return entity;
    }

    private static ReleaseDecision toDomain(ReleaseDecisionEntity entity) {
        return ReleaseDecision.restore(
                new ReleaseDecisionId(entity.getId()),
                new TenantId(entity.getTenantId()),
                new EvaluationRunId(entity.getEvaluationRunId()),
                new EvaluationTarget(entity.getAssistantVersionId(), entity.getAssistantVersionRevision()),
                entity.getPolicyVersion(),
                ReleaseDecisionStatus.valueOf(entity.getStatus()),
                ReleaseDecisionReasonCode.valueOf(entity.getReasonCode()),
                entity.getVersion(),
                entity.getDecidedAt());
    }
}

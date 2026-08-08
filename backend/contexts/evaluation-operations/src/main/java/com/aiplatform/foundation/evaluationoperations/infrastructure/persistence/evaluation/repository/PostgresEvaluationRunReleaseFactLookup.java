package com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunReleaseFact;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunStatus;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationTarget;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationRunReleaseFactLookup;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.entity.EvaluationRunEntity;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.mapper.EvaluationRunMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Objects;
import java.util.Optional;

/**
 * 只读取发布制度所需评测终态、目标和策略版本，不恢复私有评测正文或指标。
 */
public final class PostgresEvaluationRunReleaseFactLookup implements EvaluationRunReleaseFactLookup {

    private final EvaluationRunMapper mapper;

    public PostgresEvaluationRunReleaseFactLookup(EvaluationRunMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "评测运行映射器不能为空");
    }

    @Override
    public Optional<EvaluationRunReleaseFact> findById(TenantId tenantId, EvaluationRunId id) {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        id = Objects.requireNonNull(id, "评测运行标识不能为空");
        var entity = mapper.selectOne(new LambdaQueryWrapper<EvaluationRunEntity>()
                .eq(EvaluationRunEntity::getTenantId, tenantId.value())
                .eq(EvaluationRunEntity::getId, id.value()));
        return Optional.ofNullable(entity).map(PostgresEvaluationRunReleaseFactLookup::toFact);
    }

    private static EvaluationRunReleaseFact toFact(EvaluationRunEntity entity) {
        return new EvaluationRunReleaseFact(
                new TenantId(entity.getTenantId()),
                new EvaluationRunId(entity.getId()),
                new EvaluationTarget(entity.getAssistantVersionId(), entity.getAssistantVersionRevision()),
                entity.getGatePolicyVersion(),
                EvaluationRunStatus.valueOf(entity.getStatus()));
    }
}

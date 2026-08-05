package com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDataset;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetContentClassification;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetStatus;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationDatasetRepository;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.entity.EvaluationDatasetEntity;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.mapper.EvaluationDatasetMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Objects;
import java.util.Optional;

/**
 * 只按租户和标识恢复评测数据集根，不提供私有正文读取或状态判断。
 */
public final class PostgresEvaluationDatasetRepository implements EvaluationDatasetRepository {

    private final EvaluationDatasetMapper mapper;

    public PostgresEvaluationDatasetRepository(EvaluationDatasetMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "评测数据集映射器不能为空");
    }

    @Override
    public Optional<EvaluationDataset> findById(TenantId tenantId, EvaluationDatasetId id) {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        id = Objects.requireNonNull(id, "评测数据集标识不能为空");
        var entity = mapper.selectOne(new LambdaQueryWrapper<EvaluationDatasetEntity>()
                .eq(EvaluationDatasetEntity::getTenantId, tenantId.value())
                .eq(EvaluationDatasetEntity::getId, id.value()));
        return Optional.ofNullable(entity).map(PostgresEvaluationDatasetRepository::toDomain);
    }

    private static EvaluationDataset toDomain(EvaluationDatasetEntity entity) {
        return EvaluationDataset.restore(
                new TenantId(entity.getTenantId()),
                new EvaluationDatasetId(entity.getId()),
                entity.getPrivateContentReference(),
                entity.getContentDigest(),
                entity.getEntryCount(),
                EvaluationDatasetContentClassification.valueOf(entity.getClassification()),
                entity.getRetentionPolicyId(),
                EvaluationDatasetStatus.valueOf(entity.getStatus()),
                entity.getVersion(),
                entity.getCreatedAt());
    }
}

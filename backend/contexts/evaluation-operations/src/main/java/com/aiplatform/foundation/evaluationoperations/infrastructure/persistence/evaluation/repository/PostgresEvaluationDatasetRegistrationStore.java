package com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetRegistrationStore;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDataset;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.entity.EvaluationDatasetEntity;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.mapper.EvaluationDatasetMapper;
import java.util.Objects;

/**
 * 将已由领域根验证的数据集清单原样提交到 PostgreSQL；不承载状态机或正文判断。
 */
public final class PostgresEvaluationDatasetRegistrationStore
        implements EvaluationDatasetRegistrationStore {

    private final EvaluationDatasetMapper mapper;

    public PostgresEvaluationDatasetRegistrationStore(EvaluationDatasetMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "评测数据集映射器不能为空");
    }

    @Override
    public void commit(EvaluationDataset dataset) {
        dataset = Objects.requireNonNull(dataset, "评测数据集不能为空");
        if (mapper.insert(toEntity(dataset)) != 1) {
            throw new IllegalStateException("评测数据集清单提交失败");
        }
    }

    private static EvaluationDatasetEntity toEntity(EvaluationDataset dataset) {
        var entity = new EvaluationDatasetEntity();
        entity.setTenantId(dataset.tenantId().value());
        entity.setId(dataset.id().value());
        entity.setPrivateContentReference(dataset.privateContentReference());
        entity.setContentDigest(dataset.contentDigest());
        entity.setEntryCount(dataset.entryCount());
        entity.setClassification(dataset.classification().name());
        entity.setRetentionPolicyId(dataset.retentionPolicyId());
        entity.setStatus(dataset.status().name());
        entity.setVersion(dataset.version());
        entity.setCreatedAt(dataset.createdAt());
        return entity;
    }
}

package com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.mapper;

import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.entity.EvaluationDatasetEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 评测数据集清单 Mapper；字段集刻意不包含任何问题、答案或正文。
 */
public interface EvaluationDatasetMapper extends BaseMapper<EvaluationDatasetEntity> {
}

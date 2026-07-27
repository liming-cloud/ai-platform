package com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.entity.ModelDeploymentRequestEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 模型部署幂等请求 MyBatis-Plus 映射器。
 */
public interface ModelDeploymentRequestMapper
        extends BaseMapper<ModelDeploymentRequestEntity> {
}

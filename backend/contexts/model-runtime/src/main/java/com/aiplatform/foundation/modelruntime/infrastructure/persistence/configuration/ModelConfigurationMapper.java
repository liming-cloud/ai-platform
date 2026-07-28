package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity.ModelConfigurationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 受控目录与模型配置的持久化映射器。
 */
public interface ModelConfigurationMapper extends BaseMapper<ModelConfigurationEntity> {
}

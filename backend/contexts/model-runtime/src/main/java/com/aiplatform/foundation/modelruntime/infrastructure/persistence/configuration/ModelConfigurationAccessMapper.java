package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity.ModelConfigurationAccessGrantEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 模型配置授权事实的 MyBatis 映射器。
 */
public interface ModelConfigurationAccessMapper
        extends BaseMapper<ModelConfigurationAccessGrantEntity> {
}

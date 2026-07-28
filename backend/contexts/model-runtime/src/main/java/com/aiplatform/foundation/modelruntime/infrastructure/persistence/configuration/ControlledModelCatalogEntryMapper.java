package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity.ControlledModelCatalogEntryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 受控模型目录的 MyBatis-Plus 映射器。
 */
public interface ControlledModelCatalogEntryMapper
        extends BaseMapper<ControlledModelCatalogEntryEntity> {
}

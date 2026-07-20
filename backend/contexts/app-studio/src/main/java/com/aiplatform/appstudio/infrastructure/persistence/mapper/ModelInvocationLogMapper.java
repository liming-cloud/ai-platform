package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import com.aiplatform.appstudio.infrastructure.persistence.entity.ModelInvocationLogPersistenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelInvocationLogMapper extends BaseMapper<ModelInvocationLogPersistenceEntity> {
}

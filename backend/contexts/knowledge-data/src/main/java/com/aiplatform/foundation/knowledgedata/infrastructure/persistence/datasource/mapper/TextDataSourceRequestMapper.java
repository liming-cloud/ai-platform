package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper;

import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.TextDataSourceRequestEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 文本数据源幂等请求 MyBatis-Plus 映射器。
 */
public interface TextDataSourceRequestMapper
        extends BaseMapper<TextDataSourceRequestEntity> {
}

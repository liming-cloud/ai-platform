package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper;

import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity.QueryFeedbackEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 查询反馈原子去重写入映射器。
 */
public interface QueryFeedbackMapper {

    int insertIfAbsent(@Param("feedback") QueryFeedbackEntity feedback);
}

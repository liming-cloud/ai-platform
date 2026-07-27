package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper;

import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity.QueryRunEventEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 查询运行事件 MyBatis-Plus 映射器。
 */
public interface QueryRunEventMapper extends BaseMapper<QueryRunEventEntity> {

    List<QueryRunEventEntity> findAfter(
            @Param("tenantId") long tenantId,
            @Param("queryRunId") long queryRunId,
            @Param("afterSequence") long afterSequence,
            @Param("limit") int limit);
}

package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper;

import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity.QueryRunEntity;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.projection.QueryUsageRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/**
 * 查询运行、幂等占位和引用事实数据库映射器。
 */
public interface QueryRunMapper extends BaseMapper<QueryRunEntity> {

    String lockScope(@Param("scope") String scope);

    Instant findEarliestActiveLeaseExpiry(
            @Param("tenantId") long tenantId,
            @Param("acceptedAt") Instant acceptedAt);

    QueryUsageRow summarizeUsage(
            @Param("tenantId") long tenantId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("currentPeriodStart") Instant currentPeriodStart,
            @Param("currentPeriodEnd") Instant currentPeriodEnd,
            @Param("observedAt") Instant observedAt);

    int completeRun(
            @Param("tenantId") long tenantId,
            @Param("queryRunId") long queryRunId,
            @Param("status") String status,
            @Param("answer") String answer,
            @Param("refusalReason") String refusalReason,
            @Param("inputTokens") int inputTokens,
            @Param("outputTokens") int outputTokens,
            @Param("totalTokens") int totalTokens,
            @Param("newVersion") long newVersion,
            @Param("completedAt") Instant completedAt,
            @Param("expectedVersion") long expectedVersion);

}

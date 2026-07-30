package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper;

import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity.AssistantTestRunEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/**
 * 草稿测试运行及其独立配额数据库映射器。
 */
public interface AssistantTestRunMapper extends BaseMapper<AssistantTestRunEntity> {

    String lockScope(@Param("scope") String scope);

    Instant findEarliestActiveLeaseExpiry(
            @Param("tenantId") long tenantId,
            @Param("acceptedAt") Instant acceptedAt);

    int completeRun(
            @Param("tenantId") long tenantId,
            @Param("runId") long runId,
            @Param("status") String status,
            @Param("refusalReason") String refusalReason,
            @Param("inputTokens") int inputTokens,
            @Param("outputTokens") int outputTokens,
            @Param("totalTokens") int totalTokens,
            @Param("newVersion") long newVersion,
            @Param("completedAt") Instant completedAt,
            @Param("expectedVersion") long expectedVersion);
}

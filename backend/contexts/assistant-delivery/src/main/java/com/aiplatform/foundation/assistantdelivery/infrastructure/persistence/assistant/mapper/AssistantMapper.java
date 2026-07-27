package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper;

import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity.AssistantEntity;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.projection.AssistantRequestRow;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.projection.PublishedAssistantRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/**
 * 助手身份、不可变版本与幂等事实数据库映射器。
 */
public interface AssistantMapper extends BaseMapper<AssistantEntity> {

    String lockScope(@Param("scope") String scope);

    AssistantRequestRow findRequest(
            @Param("tenantId") long tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash);

    int insertRequest(
            @Param("tenantId") long tenantId,
            @Param("operation") String operation,
            @Param("keyHash") String keyHash,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("assistantId") long assistantId,
            @Param("assistantVersionId") Long assistantVersionId,
            @Param("createdAt") Instant createdAt);

    AssistantEntity findAssistantForUpdate(
            @Param("tenantId") long tenantId,
            @Param("assistantId") long assistantId);

    IPage<PublishedAssistantRow> findPublishedPage(
            IPage<PublishedAssistantRow> page,
            @Param("tenantId") long tenantId);

}

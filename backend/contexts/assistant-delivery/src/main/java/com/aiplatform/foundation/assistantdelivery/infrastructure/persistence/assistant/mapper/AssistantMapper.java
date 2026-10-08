package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper;

import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity.AssistantEntity;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.projection.PublishedAssistantRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

/**
 * 助手身份、不可变版本与幂等事实数据库映射器。
 */
public interface AssistantMapper extends BaseMapper<AssistantEntity> {

    String lockScope(@Param("scope") String scope);

    AssistantEntity findAssistantForUpdate(
            @Param("tenantId") long tenantId,
            @Param("assistantId") long assistantId);

    IPage<PublishedAssistantRow> findPublishedPage(
            IPage<PublishedAssistantRow> page,
            @Param("tenantId") long tenantId,
            @Param("filter") com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantFilter filter);

}

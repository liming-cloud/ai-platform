package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper;

import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity.AssistantRequestEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 助手幂等请求的 MyBatis-Plus 单表映射器。
 */
public interface AssistantRequestMapper
        extends BaseMapper<AssistantRequestEntity> {
}

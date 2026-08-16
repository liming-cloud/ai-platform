package com.aiplatform.foundation.assistantdelivery.application.operations.facade;

import com.aiplatform.foundation.assistantdelivery.application.operations.dto.AssistantOperationsFacts;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 向运营上下文提供助手状态计数，不返回系统指令、问题、回答或引用正文。 */
public interface AssistantOperationsFactSource {

    AssistantOperationsFacts tenantSummary(TenantId tenantId);
}

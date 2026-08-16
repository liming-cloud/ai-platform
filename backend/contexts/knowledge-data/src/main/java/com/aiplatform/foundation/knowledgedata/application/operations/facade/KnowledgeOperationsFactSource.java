package com.aiplatform.foundation.knowledgedata.application.operations.facade;

import com.aiplatform.foundation.knowledgedata.application.operations.dto.KnowledgeOperationsFacts;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 向运营上下文提供知识资源脱敏计数，不返回数据正文、对象键或索引内容。 */
public interface KnowledgeOperationsFactSource {

    KnowledgeOperationsFacts platformSummary();

    KnowledgeOperationsFacts tenantSummary(TenantId tenantId);
}

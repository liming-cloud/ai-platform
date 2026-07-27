package com.aiplatform.foundation.trustedquery.api.operations;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/**
 * 向运营上下文发布脱敏查询事实，不暴露查询正文、回答和引用内容。
 */
public interface QueryOperationsFactSource {

    QueryUsageFacts summarize(QueryUsageFactQuery query);

    QueryAuditFactPage listAuditRecords(
            TenantId tenantId,
            String cursor,
            int limit);
}

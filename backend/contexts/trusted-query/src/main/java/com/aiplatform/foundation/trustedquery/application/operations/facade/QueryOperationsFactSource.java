package com.aiplatform.foundation.trustedquery.application.operations.facade;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.trustedquery.application.operations.dto.QueryAuditFact;
import com.aiplatform.foundation.trustedquery.application.operations.dto.QueryUsageFacts;
import com.aiplatform.foundation.trustedquery.application.operations.query.QueryUsageFactQuery;

/**
 * 向运营上下文发布脱敏查询事实，不暴露查询正文、回答和引用内容。
 */
public interface QueryOperationsFactSource {

    QueryUsageFacts summarize(QueryUsageFactQuery query);

    PageResult<QueryAuditFact> listAuditRecords(
            TenantId tenantId,
            PageQuery page);
}

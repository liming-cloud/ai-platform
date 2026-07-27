package com.aiplatform.foundation.evaluationoperations.api;

import com.aiplatform.foundation.evaluationoperations.api.model.ListQueryAuditRecordsQuery;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryAuditRecordView;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryUsageQuery;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryUsageView;
import com.aiplatform.foundation.shared.contract.paging.PageResult;

/**
 * 评测与运营上下文的查询用量和脱敏审计公共契约。
 */
public interface OperationsQueryUseCase {

    QueryUsageView usage(QueryUsageQuery query);

    PageResult<QueryAuditRecordView> audit(ListQueryAuditRecordsQuery query);
}

package com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.dto.EvaluationDatasetCandidate;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 按租户读取已发布评测数据集安全摘要的服务端分页契约。 */
@FunctionalInterface
public interface EvaluationDatasetCandidateDirectory {

    PageResult<EvaluationDatasetCandidate> pagePublished(TenantId tenantId, PageQuery page);
}

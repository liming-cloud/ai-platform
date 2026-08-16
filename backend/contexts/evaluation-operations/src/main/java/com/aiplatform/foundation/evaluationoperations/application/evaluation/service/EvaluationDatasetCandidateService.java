package com.aiplatform.foundation.evaluationoperations.application.evaluation.service;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.dto.EvaluationDatasetCandidate;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetCandidateDirectory;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;

/** 为评测任务提供当前租户已发布数据集的名称化安全选择器。 */
public final class EvaluationDatasetCandidateService {

    private static final PermissionCode ASSISTANT_WRITE = new PermissionCode("assistant:write");
    private final EvaluationDatasetCandidateDirectory directory;

    public EvaluationDatasetCandidateService(EvaluationDatasetCandidateDirectory directory) {
        this.directory = Objects.requireNonNull(directory, "评测数据集候选目录不能为空");
    }

    public PageResult<EvaluationDatasetCandidate> page(TrustedRequestContext context, PageQuery page) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        page = Objects.requireNonNull(page, "分页查询不能为空");
        if (!context.hasPermission(ASSISTANT_WRITE)) {
            throw new IllegalArgumentException("当前成员没有发起评测的权限");
        }
        return directory.pagePublished(context.tenantId(), page);
    }
}

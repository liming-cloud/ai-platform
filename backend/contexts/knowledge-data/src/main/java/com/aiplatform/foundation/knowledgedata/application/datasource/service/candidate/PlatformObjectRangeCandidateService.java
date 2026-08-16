package com.aiplatform.foundation.knowledgedata.application.datasource.service.candidate;

import com.aiplatform.foundation.knowledgedata.application.datasource.dto.PlatformObjectRangeCandidate;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectRangeCandidateDirectory;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;

/** 为对象存储登记任务提供当前租户可理解的授权范围选择器。 */
public final class PlatformObjectRangeCandidateService {
    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");
    private final PlatformObjectRangeCandidateDirectory directory;

    public PlatformObjectRangeCandidateService(PlatformObjectRangeCandidateDirectory directory) {
        this.directory = Objects.requireNonNull(directory, "对象范围候选目录不能为空");
    }

    public PageResult<PlatformObjectRangeCandidate> page(
            TrustedRequestContext context, String knowledgeSpaceId, PageQuery page) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (!context.hasPermission(WRITE)) {
            throw new IllegalArgumentException("当前成员没有登记数据来源的权限");
        }
        return directory.page(context.tenantId(), KnowledgeSpaceId.parse(knowledgeSpaceId),
                Objects.requireNonNull(page, "分页查询不能为空"));
    }
}

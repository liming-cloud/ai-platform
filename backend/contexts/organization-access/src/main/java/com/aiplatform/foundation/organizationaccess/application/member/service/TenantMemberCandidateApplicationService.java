package com.aiplatform.foundation.organizationaccess.application.member.service;

import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantMemberCandidate;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberCandidateDirectory;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;

/** 为当前租户的新成员任务提供服务端分页账户名称选择器。 */
public final class TenantMemberCandidateApplicationService {

    private static final PermissionCode ACCESS_MANAGE = new PermissionCode("access:manage");
    private final TenantMemberCandidateDirectory directory;

    public TenantMemberCandidateApplicationService(TenantMemberCandidateDirectory directory) {
        this.directory = Objects.requireNonNull(directory, "租户成员候选目录不能为空");
    }

    public PageResult<TenantMemberCandidate> search(
            TrustedRequestContext context,
            String query,
            PageQuery page) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        page = Objects.requireNonNull(page, "分页查询不能为空");
        if (!context.hasPermission(ACCESS_MANAGE)) {
            throw new IllegalArgumentException("当前成员没有成员候选目录权限");
        }
        query = query == null ? "" : query.trim();
        if (query.codePointCount(0, query.length()) > 64) {
            throw new IllegalArgumentException("账户搜索词不能超过六十四个字符");
        }
        return directory.searchEligible(context.tenantId(), query, page);
    }
}

package com.aiplatform.foundation.organizationaccess.application.authorization.service.candidate;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountCandidate;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.candidate.PlatformAccountCandidateDirectory;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;

/** 为需要关联账户的产品任务提供服务端分页名称选择器。 */
public final class PlatformAccountCandidateApplicationService {

    private final PlatformAccountCandidateDirectory directory;

    public PlatformAccountCandidateApplicationService(PlatformAccountCandidateDirectory directory) {
        this.directory = Objects.requireNonNull(directory, "平台账户候选目录不能为空");
    }

    public PageResult<PlatformAccountCandidate> search(
            PlatformRequestContext context,
            AccountCandidatePurpose purpose,
            String query,
            PageQuery page) {
        context = Objects.requireNonNull(context, "可信平台上下文不能为空");
        Objects.requireNonNull(purpose, "账户候选用途不能为空");
        page = Objects.requireNonNull(page, "分页查询不能为空");
        if (!context.hasPermission(PlatformPermissions.ACCOUNT_MANAGE)) {
            throw new IllegalArgumentException("当前主体没有账户候选目录权限");
        }
        query = query == null ? "" : query.trim();
        if (query.codePointCount(0, query.length()) > 64) {
            throw new IllegalArgumentException("账户搜索词不能超过六十四个字符");
        }
        return directory.search(query, page);
    }
}

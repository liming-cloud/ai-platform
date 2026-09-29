package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountSummary;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountDirectory;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;

/** 只向账户治理角色提供服务端权威的平台账户目录。 */
public final class PlatformAccountDirectoryApplicationService {
    private final PlatformAccountDirectory directory;

    public PlatformAccountDirectoryApplicationService(PlatformAccountDirectory directory) {
        this.directory = Objects.requireNonNull(directory, "平台账户目录不能为空");
    }

    public PageResult<PlatformAccountSummary> list(
            PlatformRequestContext context, PageQuery query, String loginName, String status) {
        context = Objects.requireNonNull(context, "可信平台上下文不能为空");
        query = Objects.requireNonNull(query, "分页查询不能为空");
        if (!context.hasPermission(PlatformPermissions.ACCOUNT_MANAGE)) {
            throw new IllegalArgumentException("当前主体没有账户目录权限");
        }
        loginName = Objects.requireNonNull(loginName, "登录名搜索词不能为空").trim();
        status = Objects.requireNonNull(status, "账户状态不能为空");
        if (loginName.length() > 64 || !(status.equals("ALL") || status.equals("ACTIVE") || status.equals("DISABLED"))) {
            throw new IllegalArgumentException("账户目录筛选条件无效");
        }
        if (query.pageSize() != 10 && query.pageSize() != 20
                && query.pageSize() != 50 && query.pageSize() != 100) {
            throw new IllegalArgumentException("账户目录页大小无效");
        }
        return directory.list(query, loginName, status);
    }
}

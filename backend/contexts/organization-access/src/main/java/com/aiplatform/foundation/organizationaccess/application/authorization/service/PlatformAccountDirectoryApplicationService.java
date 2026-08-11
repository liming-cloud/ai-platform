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

    public PageResult<PlatformAccountSummary> list(PlatformRequestContext context, PageQuery query) {
        context = Objects.requireNonNull(context, "可信平台上下文不能为空");
        query = Objects.requireNonNull(query, "分页查询不能为空");
        if (!context.hasPermission(PlatformPermissions.ACCOUNT_MANAGE)) {
            throw new IllegalArgumentException("当前主体没有账户目录权限");
        }
        return directory.list(query);
    }
}

package com.aiplatform.foundation.organizationaccess.application.member.service;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountCandidate;
import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantAdministratorSummary;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.PlatformTenantAdministratorDirectory;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 平台受权读取租户管理员目录与受控候选。 */
public class PlatformTenantAdministratorQueryService {

    private final PlatformTenantAdministratorDirectory directory;

    public PlatformTenantAdministratorQueryService(PlatformTenantAdministratorDirectory directory) {
        this.directory = Objects.requireNonNull(directory, "租户管理员目录不能为空");
    }

    /** 分页读取目标租户的管理员安全摘要。 */
    @Transactional(readOnly = true)
    public PageResult<TenantAdministratorSummary> list(
            PlatformRequestContext context,
            String tenantId,
            String query,
            String status,
            PageQuery page) {
        requireTenantGovernor(context);
        query = query == null ? "" : query.trim();
        if (query.codePointCount(0, query.length()) > 64) {
            throw new IllegalArgumentException("管理员搜索词不能超过六十四个字符");
        }
        if (!java.util.Set.of("ALL", "ENABLED", "DISABLED").contains(status)) {
            throw new IllegalArgumentException("管理员状态筛选无效");
        }
        return directory.page(TenantId.parse(tenantId), query, status,
                Objects.requireNonNull(page, "分页查询不能为空"));
    }

    /** 分页搜索可分配为目标租户管理员的活动账户。 */
    @Transactional(readOnly = true)
    public PageResult<PlatformAccountCandidate> candidates(
            PlatformRequestContext context,
            String tenantId,
            String query,
            PageQuery page) {
        requireTenantGovernor(context);
        query = query == null ? "" : query.trim();
        if (query.codePointCount(0, query.length()) > 64) {
            throw new IllegalArgumentException("账户搜索词不能超过六十四个字符");
        }
        return directory.candidates(
                TenantId.parse(tenantId), query, Objects.requireNonNull(page, "分页查询不能为空"));
    }

    private static void requireTenantGovernor(PlatformRequestContext context) {
        context = Objects.requireNonNull(context, "可信平台上下文不能为空");
        if (!context.hasPermission(PlatformPermissions.TENANT_GOVERNANCE_WRITE)) {
            throw new IllegalArgumentException("当前主体没有租户管理员目录权限");
        }
    }
}

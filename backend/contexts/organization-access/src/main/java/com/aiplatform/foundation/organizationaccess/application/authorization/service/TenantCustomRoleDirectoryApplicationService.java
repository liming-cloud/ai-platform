package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantCustomRoleSummary;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleDirectory;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 受权分页读取当前租户自定义角色目录。 */
public class TenantCustomRoleDirectoryApplicationService {

    private static final PermissionCode ACCESS_MANAGE = new PermissionCode("access:manage");

    private final TenantCustomRoleDirectory directory;

    /** 构造自定义角色目录读取用例。 */
    public TenantCustomRoleDirectoryApplicationService(TenantCustomRoleDirectory directory) {
        this.directory = Objects.requireNonNull(directory, "自定义角色目录不能为空");
    }

    /**
     * 读取当前令牌所属租户的一页自定义角色。
     *
     * @throws IllegalArgumentException 当当前成员不具备访问治理权限时拒绝。
     */
    @Transactional(readOnly = true)
    public PageResult<TenantCustomRoleSummary> list(TrustedRequestContext context, PageQuery page) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        page = Objects.requireNonNull(page, "分页查询不能为空");
        if (!context.hasPermission(ACCESS_MANAGE)) {
            throw new IllegalArgumentException("当前成员没有访问治理权限");
        }
        return directory.page(context.tenantId(), page);
    }
}

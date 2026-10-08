package com.aiplatform.foundation.organizationaccess.application.member.service;

import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantMemberSummary;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberDirectory;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 受权分页读取当前租户成员目录。 */
public class TenantMemberDirectoryApplicationService {

    private static final PermissionCode ACCESS_MANAGE = new PermissionCode("access:manage");

    private final TenantMemberDirectory directory;

    /** 构造成员目录读取用例。 */
    public TenantMemberDirectoryApplicationService(TenantMemberDirectory directory) {
        this.directory = Objects.requireNonNull(directory, "成员目录不能为空");
    }

    /**
     * 读取当前令牌所属租户的成员目录。
     *
     * @throws IllegalArgumentException 当当前成员不具备成员治理权限时拒绝。
     */
    @Transactional(readOnly = true)
    public PageResult<TenantMemberSummary> list(
            TrustedRequestContext context,
            PageQuery page, String q, String status) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        page = Objects.requireNonNull(page, "分页查询不能为空");
        if (!context.hasPermission(ACCESS_MANAGE)) {
            throw new IllegalArgumentException("当前成员没有成员治理权限");
        }
        q = Objects.requireNonNull(q, "搜索词不能为空").strip();
        status = Objects.requireNonNull(status, "状态筛选不能为空");
        if (q.length() > 64 || !java.util.Set.of("", "ENABLED", "DISABLED").contains(status)) {
            throw new IllegalArgumentException("成员目录筛选不合法");
        }
        return directory.page(context.tenantId(), page, q, status);
    }
}

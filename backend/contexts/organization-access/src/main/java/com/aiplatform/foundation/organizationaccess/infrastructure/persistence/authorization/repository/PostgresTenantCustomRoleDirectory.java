package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantCustomRoleSummary;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleDirectory;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleDirectoryMapper;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** PostgreSQL 自定义角色目录读取适配器，只映射当前租户最小投影和分页元数据。 */
public final class PostgresTenantCustomRoleDirectory implements TenantCustomRoleDirectory {

    private final TenantCustomRoleDirectoryMapper mapper;

    /** 构造 PostgreSQL 自定义角色目录读取适配器。 */
    public PostgresTenantCustomRoleDirectory(TenantCustomRoleDirectoryMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "自定义角色目录映射器不能为空");
    }

    @Override
    public PageResult<TenantCustomRoleSummary> page(TenantId tenantId, PageQuery page) {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        page = Objects.requireNonNull(page, "分页查询不能为空");
        var items = mapper.selectPage(tenantId.value(), page.offset(), page.pageSize()).stream()
                .map(row -> new TenantCustomRoleSummary(
                        Long.toString(row.getCustomRoleId()), row.getRoleName(), permissions(row.getPermissionCodes()),
                        row.getVersion(), row.getStatus(), row.getBoundMemberCount()))
                .toList();
        return PageResult.of(items, page, mapper.count(tenantId.value()));
    }

    private static List<String> permissions(String encodedPermissions) {
        if (encodedPermissions == null || encodedPermissions.isBlank()) {
            return List.of();
        }
        return Arrays.stream(encodedPermissions.split(",")).filter(code -> !code.isBlank()).toList();
    }
}

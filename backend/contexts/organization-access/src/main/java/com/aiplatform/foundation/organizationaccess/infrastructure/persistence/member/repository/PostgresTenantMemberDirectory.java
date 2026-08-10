package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantMemberSummary;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberDirectory;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberDirectoryMapper;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** PostgreSQL 成员目录读取适配器，只映射当前租户最小投影和分页元数据。 */
public final class PostgresTenantMemberDirectory implements TenantMemberDirectory {

    private final TenantMemberDirectoryMapper mapper;

    /** 构造 PostgreSQL 成员目录读取适配器。 */
    public PostgresTenantMemberDirectory(TenantMemberDirectoryMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "成员目录映射器不能为空");
    }

    @Override
    public PageResult<TenantMemberSummary> page(TenantId tenantId, PageQuery page) {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        page = Objects.requireNonNull(page, "分页查询不能为空");
        var items = mapper.selectPage(tenantId.value(), page.offset(), page.pageSize()).stream()
                .map(row -> new TenantMemberSummary(
                        Long.toString(row.getMembershipId()),
                        row.getLoginName(),
                        row.getMembershipRole(),
                        row.getStatus(),
                        row.getPermissionVersion(),
                        roles(row.getBuiltInRoleCodes()),
                        row.getCreatedAt()))
                .toList();
        return PageResult.of(items, page, mapper.count(tenantId.value()));
    }

    private static List<String> roles(String encodedRoles) {
        if (encodedRoles == null || encodedRoles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(encodedRoles.split(","))
                .filter(role -> !role.isBlank())
                .toList();
    }
}

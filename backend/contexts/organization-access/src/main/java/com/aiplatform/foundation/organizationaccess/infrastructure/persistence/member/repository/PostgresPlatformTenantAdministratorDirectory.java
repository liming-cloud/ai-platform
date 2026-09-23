package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountCandidate;
import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantAdministratorSummary;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.PlatformTenantAdministratorDirectory;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.PlatformTenantAdministratorMapper;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Locale;
import java.util.Objects;

/** PostgreSQL 平台租户管理员目录，只返回服务端分页安全摘要。 */
public final class PostgresPlatformTenantAdministratorDirectory
        implements PlatformTenantAdministratorDirectory {

    private final PlatformTenantAdministratorMapper mapper;

    public PostgresPlatformTenantAdministratorDirectory(PlatformTenantAdministratorMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "租户管理员映射器不能为空");
    }

    @Override
    public PageResult<TenantAdministratorSummary> page(
            TenantId tenantId, String query, String status, PageQuery page) {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        page = Objects.requireNonNull(page, "分页查询不能为空");
        var pattern = "%" + escapeLike(Objects.requireNonNull(query, "搜索词不能为空")
                .toLowerCase(Locale.ROOT)) + "%";
        var items = mapper.selectAdministratorPage(
                        tenantId.value(), pattern, status, page.offset(), page.pageSize()).stream()
                .map(row -> new TenantAdministratorSummary(
                        Long.toString(row.getMembershipId()),
                        row.getLoginName(),
                        row.getStatus(),
                        row.getPermissionVersion(),
                        row.getVersion(),
                        row.getCreatedAt(), row.getLastSecurityChangeAt()))
                .toList();
        return PageResult.of(items, page, mapper.countAdministrators(tenantId.value(), pattern, status));
    }

    @Override
    public PageResult<PlatformAccountCandidate> candidates(
            TenantId tenantId,
            String query,
            PageQuery page) {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        page = Objects.requireNonNull(page, "分页查询不能为空");
        var pattern = "%" + escapeLike(Objects.requireNonNull(query, "搜索词不能为空")
                .toLowerCase(Locale.ROOT)) + "%";
        var items = mapper.selectCandidatePage(
                        tenantId.value(), pattern, page.offset(), page.pageSize()).stream()
                .map(row -> new PlatformAccountCandidate(
                        Long.toString(row.getSubjectId()), row.getLoginName(), row.getStatus()))
                .toList();
        return PageResult.of(items, page, mapper.countCandidates(tenantId.value(), pattern));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

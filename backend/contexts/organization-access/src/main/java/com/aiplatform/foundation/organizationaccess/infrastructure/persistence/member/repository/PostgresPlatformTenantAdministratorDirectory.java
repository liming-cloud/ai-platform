package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountCandidate;
import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantAdministratorSummary;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.PlatformTenantAdministratorDirectory;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.PlatformTenantAdministratorMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.projection.PlatformTenantAdministratorCandidateRow;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.projection.PlatformTenantAdministratorSummaryRow;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
        var pagination = new Page<PlatformTenantAdministratorSummaryRow>(page.pageNumber(), page.pageSize());
        // 显式复用同范围计数，避免自动改写关联查询时丢失管理员资格或状态筛选。
        pagination.setCountId("countAdministrators");
        var result = mapper.selectAdministratorPage(pagination, tenantId.value(), pattern, status);
        var items = result.getRecords().stream()
                .map(row -> new TenantAdministratorSummary(
                        Long.toString(row.getMembershipId()),
                        row.getLoginName(),
                        row.getStatus(),
                        row.getPermissionVersion(),
                        row.getVersion(),
                        row.getCreatedAt(), row.getLastSecurityChangeAt()))
                .toList();
        return PageResult.of(items, page, result.getTotal());
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
        var pagination = new Page<PlatformTenantAdministratorCandidateRow>(page.pageNumber(), page.pageSize());
        // 候选总数沿用排除平台角色及任何租户管理员归属的完整安全谓词。
        pagination.setCountId("countCandidates");
        var result = mapper.selectCandidatePage(pagination, tenantId.value(), pattern);
        var items = result.getRecords().stream()
                .map(row -> new PlatformAccountCandidate(
                        Long.toString(row.getSubjectId()), row.getLoginName(), row.getStatus()))
                .toList();
        return PageResult.of(items, page, result.getTotal());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

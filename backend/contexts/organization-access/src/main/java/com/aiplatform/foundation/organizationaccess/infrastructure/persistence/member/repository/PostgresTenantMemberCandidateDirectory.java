package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantMemberCandidate;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberCandidateDirectory;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberCandidateMapper;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Locale;
import java.util.Objects;

/** 使用 PostgreSQL 安全投影返回当前租户可以建立成员资格的活动账户。 */
public final class PostgresTenantMemberCandidateDirectory implements TenantMemberCandidateDirectory {

    private final TenantMemberCandidateMapper mapper;

    /** 构造租户成员候选账户目录。 */
    public PostgresTenantMemberCandidateDirectory(TenantMemberCandidateMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "成员候选映射器不能为空");
    }

    /**
     * 使用数据库端总数和分页查询返回候选账户，不暴露账户凭据或成员详情。
     */
    @Override
    public PageResult<TenantMemberCandidate> searchEligible(
            TenantId tenantId,
            String query,
            PageQuery page) {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        query = Objects.requireNonNull(query, "账户搜索词不能为空");
        page = Objects.requireNonNull(page, "分页查询不能为空");
        var pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
        var result = mapper.selectEligiblePage(
                new Page<>(page.pageNumber(), page.pageSize()),
                tenantId.value(),
                pattern);
        var items = result.getRecords().stream()
                .map(row -> new TenantMemberCandidate(
                        Long.toString(row.getSubjectId()),
                        row.getLoginName(),
                        row.getStatus()))
                .toList();
        return PageResult.of(items, page, result.getTotal());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

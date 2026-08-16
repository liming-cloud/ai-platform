package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantMemberCandidate;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberCandidateDirectory;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Locale;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 使用 PostgreSQL 排除当前租户已有成员，只返回可选账户的名称摘要。 */
public final class JdbcTenantMemberCandidateDirectory implements TenantMemberCandidateDirectory {

    private final JdbcClient jdbc;

    public JdbcTenantMemberCandidateDirectory(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC 客户端不能为空");
    }

    @Override
    public PageResult<TenantMemberCandidate> searchEligible(
            TenantId tenantId,
            String query,
            PageQuery page) {
        var pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
        var predicate = """
                FROM organization_access.subject_identity account
                WHERE account.status = 'ACTIVE'
                  AND lower(account.login_name) LIKE :pattern ESCAPE '\\'
                  AND NOT EXISTS (
                      SELECT 1 FROM organization_access.tenant_membership member
                      WHERE member.tenant_id = :tenantId
                        AND member.subject_id = account.subject_id
                  )
                """;
        var total = jdbc.sql("SELECT COUNT(*) " + predicate)
                .param("pattern", pattern)
                .param("tenantId", tenantId.value())
                .query(Long.class)
                .single();
        var items = jdbc.sql("""
                        SELECT account.subject_id, account.login_name, account.status
                        """ + predicate + """
                        ORDER BY lower(account.login_name), account.subject_id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("pattern", pattern)
                .param("tenantId", tenantId.value())
                .param("limit", page.pageSize())
                .param("offset", page.offset())
                .query((row, rowNumber) -> new TenantMemberCandidate(
                        Long.toString(row.getLong("subject_id")),
                        row.getString("login_name"),
                        row.getString("status")))
                .list();
        return PageResult.of(items, page, total);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

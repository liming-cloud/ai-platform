package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.operations;

import com.aiplatform.foundation.organizationaccess.application.operations.dto.PlatformOrganizationOperationsFacts;
import com.aiplatform.foundation.organizationaccess.application.operations.dto.TenantOperationsIdentity;
import com.aiplatform.foundation.organizationaccess.application.operations.dto.TenantOrganizationOperationsFacts;
import com.aiplatform.foundation.organizationaccess.application.operations.facade.OrganizationOperationsFactSource;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 只读取组织上下文计数列和租户安全摘要的 PostgreSQL 事实源。 */
public final class JdbcOrganizationOperationsFactSource implements OrganizationOperationsFactSource {

    private final JdbcClient jdbc;

    public JdbcOrganizationOperationsFactSource(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC 客户端不能为空");
    }

    @Override
    public PlatformOrganizationOperationsFacts platformSummary() {
        return new PlatformOrganizationOperationsFacts(
                grouped("SELECT status, COUNT(*) total FROM organization_access.tenant_lifecycle GROUP BY status"),
                grouped("""
                        SELECT subject.status, COUNT(DISTINCT binding.subject_id) total
                        FROM organization_access.platform_role_binding binding
                        JOIN organization_access.subject_identity subject ON subject.subject_id = binding.subject_id
                        WHERE binding.status = 'ACTIVE' AND binding.role_code = 'PLATFORM_ADMINISTRATOR'
                        GROUP BY subject.status
                        """),
                grouped("""
                        SELECT membership.status, COUNT(*) total
                        FROM organization_access.tenant_membership membership
                        WHERE membership.membership_role = 'TENANT_ADMINISTRATOR'
                        GROUP BY membership.status
                        """),
                grouped("""
                        SELECT membership.status, COUNT(*) total
                        FROM organization_access.tenant_membership membership
                        WHERE membership.membership_role = 'TENANT_USER'
                        GROUP BY membership.status
                        """));
    }

    @Override
    public TenantOrganizationOperationsFacts tenantSummary(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        return new TenantOrganizationOperationsFacts(
                groupedByTenant("TENANT_ADMINISTRATOR", tenantId.value()),
                groupedByTenant("TENANT_USER", tenantId.value()));
    }

    @Override
    public PageResult<TenantOperationsIdentity> listTenants(PageQuery page) {
        Objects.requireNonNull(page, "分页请求不能为空");
        var total = jdbc.sql("SELECT COUNT(*) FROM organization_access.tenant_lifecycle")
                .query(Long.class)
                .single();
        var items = jdbc.sql("""
                        SELECT tenant_id, display_name, status
                        FROM organization_access.tenant_lifecycle
                        ORDER BY lower(display_name), tenant_id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("limit", page.pageSize())
                .param("offset", page.offset())
                .query((row, rowNumber) -> new TenantOperationsIdentity(
                        row.getLong("tenant_id"),
                        row.getString("display_name"),
                        row.getString("status")))
                .list();
        return PageResult.of(items, page, total);
    }

    private Map<String, Long> groupedByTenant(String role, long tenantId) {
        var rows = jdbc.sql("""
                        SELECT status, COUNT(*) total
                        FROM organization_access.tenant_membership
                        WHERE tenant_id = :tenantId AND membership_role = :role
                        GROUP BY status
                        """)
                .param("tenantId", tenantId)
                .param("role", role)
                .query((row, rowNumber) -> Map.entry(
                        row.getString("status"), row.getLong("total")))
                .list();
        return toMap(rows);
    }

    private Map<String, Long> grouped(String sql) {
        return toMap(jdbc.sql(sql)
                .query((row, rowNumber) -> Map.entry(
                        row.getString("status"), row.getLong("total")))
                .list());
    }

    private static Map<String, Long> toMap(Iterable<Map.Entry<String, Long>> rows) {
        var counts = new LinkedHashMap<String, Long>();
        rows.forEach(row -> counts.put(row.getKey(), row.getValue()));
        return Map.copyOf(counts);
    }
}

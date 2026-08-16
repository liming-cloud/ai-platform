package com.aiplatform.foundation.modelruntime.infrastructure.persistence.operations;

import com.aiplatform.foundation.modelruntime.application.operations.dto.ModelOperationsFacts;
import com.aiplatform.foundation.modelruntime.application.operations.facade.ModelOperationsFactSource;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 只聚合模型供应链状态列的 PostgreSQL 事实源。 */
public final class JdbcModelOperationsFactSource implements ModelOperationsFactSource {

    private final JdbcClient jdbc;

    public JdbcModelOperationsFactSource(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC 客户端不能为空");
    }

    @Override
    public ModelOperationsFacts platformSummary() {
        return new ModelOperationsFacts(
                grouped("model_runtime.platform_provider_connection", null, null),
                grouped("model_runtime.platform_model_deployment", null, null),
                grouped("model_runtime.provider_connection", null, null),
                grouped("model_runtime.model_deployment", null, null),
                grouped("model_runtime.model_configuration", null, "scope <> 'PERSONAL'"));
    }

    @Override
    public ModelOperationsFacts tenantSummary(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        var value = tenantId.value();
        return new ModelOperationsFacts(
                Map.of(),
                Map.of(),
                grouped("model_runtime.provider_connection", value, null),
                grouped("model_runtime.model_deployment", value, null),
                grouped("model_runtime.model_configuration", value, "scope IN ('TENANT', 'PERSONAL')"));
    }

    @Override
    public Set<TenantId> tenantsWithoutPublishedDeployment() {
        return Set.copyOf(jdbc.sql("""
                        SELECT tenant_id
                        FROM model_runtime.model_deployment
                        GROUP BY tenant_id
                        HAVING COUNT(*) FILTER (WHERE status = 'PUBLISHED') = 0
                        """)
                .query((row, rowNumber) -> new TenantId(row.getLong("tenant_id")))
                .list());
    }

    private Map<String, Long> grouped(String table, Long tenantId, String additionalFilter) {
        var filters = new StringBuilder();
        if (tenantId != null) {
            filters.append("tenant_id = :tenantId");
        }
        if (additionalFilter != null) {
            if (!filters.isEmpty()) {
                filters.append(" AND ");
            }
            filters.append(additionalFilter);
        }
        var sql = "SELECT status, COUNT(*) total FROM " + table
                + (filters.isEmpty() ? "" : " WHERE " + filters)
                + " GROUP BY status";
        var request = jdbc.sql(sql);
        if (tenantId != null) {
            request = request.param("tenantId", tenantId);
        }
        var rows = request.query((row, rowNumber) -> Map.entry(
                        row.getString("status"), row.getLong("total")))
                .list();
        var counts = new LinkedHashMap<String, Long>();
        rows.forEach(row -> counts.put(row.getKey(), row.getValue()));
        return Map.copyOf(counts);
    }
}

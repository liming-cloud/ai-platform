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
                connectionHealth("model_runtime.platform_provider_connection", null),
                platformDeploymentAvailability(),
                connectionHealth("model_runtime.provider_connection", null),
                tenantDeploymentAvailability(null),
                lifecycleCounts("model_runtime.model_configuration", null, "scope <> 'PERSONAL'"));
    }

    @Override
    public ModelOperationsFacts tenantSummary(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        var value = tenantId.value();
        return new ModelOperationsFacts(
                Map.of(),
                Map.of(),
                connectionHealth("model_runtime.provider_connection", value),
                tenantDeploymentAvailability(value),
                lifecycleCounts("model_runtime.model_configuration", value, "scope IN ('TENANT', 'PERSONAL')"));
    }

    @Override
    public Set<TenantId> tenantsWithoutPublishedDeployment() {
        return Set.copyOf(jdbc.sql("""
                        WITH configured_tenant AS (
                            SELECT tenant_id FROM model_runtime.provider_connection
                            UNION
                            SELECT tenant_id FROM model_runtime.model_deployment
                            UNION
                            SELECT tenant_id FROM model_runtime.model_configuration
                            WHERE tenant_id IS NOT NULL
                        )
                        SELECT configured.tenant_id
                        FROM configured_tenant configured
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM model_runtime.model_deployment deployment
                            INNER JOIN model_runtime.provider_connection connection
                                ON connection.tenant_id = deployment.tenant_id
                                AND connection.id = deployment.provider_connection_id
                            WHERE deployment.tenant_id = configured.tenant_id
                                AND deployment.status = 'PUBLISHED'
                                AND deployment.verification_expires_at > CURRENT_TIMESTAMP
                                AND connection.status = 'VERIFIED'
                                AND connection.version = deployment.provider_connection_revision
                        )
                        AND NOT EXISTS (
                            SELECT 1
                            FROM model_runtime.model_configuration configuration
                            INNER JOIN model_runtime.model_configuration_credential credential
                                ON credential.model_configuration_id = configuration.id
                            INNER JOIN model_runtime.controlled_model_catalog_entry catalog
                                ON catalog.id = configuration.catalog_entry_id
                            INNER JOIN model_runtime.platform_model_deployment deployment
                                ON deployment.id = catalog.source_platform_deployment_id
                            INNER JOIN model_runtime.platform_provider_connection connection
                                ON connection.id = deployment.platform_provider_connection_id
                            WHERE configuration.tenant_id = configured.tenant_id
                                AND configuration.status = 'ACTIVE'
                                AND catalog.status = 'PUBLISHED'
                                AND deployment.version = catalog.source_platform_deployment_revision
                                AND deployment.status = 'PUBLISHED'
                                AND deployment.verification_expires_at > CURRENT_TIMESTAMP
                                AND connection.status = 'VERIFIED'
                                AND connection.version = deployment.platform_provider_connection_revision
                        )
                        """)
                .query((row, rowNumber) -> new TenantId(row.getLong("tenant_id")))
                .list());
    }

    /**
     * 连接运营状态以最近一次真实探测为准，避免把失败探测仍处于 `DRAFT` 的连接误报为普通草稿。
     */
    private Map<String, Long> connectionHealth(String table, Long tenantId) {
        var filter = tenantId == null ? "" : " WHERE tenant_id = :tenantId";
        return queryGrouped("""
                SELECT CASE
                    WHEN status = 'DISABLED' THEN 'DISABLED'
                    WHEN status = 'VERIFIED' AND last_probe_succeeded IS TRUE THEN 'VERIFIED'
                    WHEN last_probe_succeeded IS FALSE THEN 'FAILED'
                    ELSE 'PENDING'
                END status,
                COUNT(*) total
                FROM %s%s
                GROUP BY 1
                """.formatted(table, filter), tenantId);
    }

    /** 只有来源连接当前有效且验证未过期的已发布平台部署才计为可用发布。 */
    private Map<String, Long> platformDeploymentAvailability() {
        return queryGrouped("""
                SELECT CASE
                    WHEN deployment.status = 'PUBLISHED'
                        AND deployment.verification_expires_at > CURRENT_TIMESTAMP
                        AND connection.status = 'VERIFIED'
                        AND connection.version = deployment.platform_provider_connection_revision
                    THEN 'PUBLISHED'
                    ELSE 'UNAVAILABLE'
                END status,
                COUNT(*) total
                FROM model_runtime.platform_model_deployment deployment
                INNER JOIN model_runtime.platform_provider_connection connection
                    ON connection.id = deployment.platform_provider_connection_id
                GROUP BY 1
                """, null);
    }

    /** 租户部署沿用部署列表的可用性不变量，并在 SQL 聚合阶段保持租户隔离。 */
    private Map<String, Long> tenantDeploymentAvailability(Long tenantId) {
        var filter = tenantId == null ? "" : " WHERE deployment.tenant_id = :tenantId";
        return queryGrouped("""
                SELECT CASE
                    WHEN deployment.status = 'PUBLISHED'
                        AND deployment.verification_expires_at > CURRENT_TIMESTAMP
                        AND connection.status = 'VERIFIED'
                        AND connection.version = deployment.provider_connection_revision
                    THEN 'PUBLISHED'
                    ELSE 'UNAVAILABLE'
                END status,
                COUNT(*) total
                FROM model_runtime.model_deployment deployment
                INNER JOIN model_runtime.provider_connection connection
                    ON connection.tenant_id = deployment.tenant_id
                    AND connection.id = deployment.provider_connection_id%s
                GROUP BY 1
                """.formatted(filter), tenantId);
    }

    private Map<String, Long> lifecycleCounts(String table, Long tenantId, String additionalFilter) {
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
        return queryGrouped(sql, tenantId);
    }

    private Map<String, Long> queryGrouped(String sql, Long tenantId) {
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

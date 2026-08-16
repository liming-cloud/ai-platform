package com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.operations;

import com.aiplatform.foundation.evaluationoperations.application.operations.dto.EvaluationOperationsFacts;
import com.aiplatform.foundation.evaluationoperations.application.operations.port.out.EvaluationOperationsFactSource;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 只聚合评测运行与发布决定状态列的 PostgreSQL 事实源。 */
public final class JdbcEvaluationOperationsFactSource implements EvaluationOperationsFactSource {

    private final JdbcClient jdbc;

    public JdbcEvaluationOperationsFactSource(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC 客户端不能为空");
    }

    @Override
    public EvaluationOperationsFacts tenantSummary(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        return new EvaluationOperationsFacts(
                grouped("evaluation_operations.evaluation_run", tenantId.value()),
                grouped("evaluation_operations.release_decision", tenantId.value()));
    }

    private Map<String, Long> grouped(String table, long tenantId) {
        var rows = jdbc.sql("SELECT status, COUNT(*) total FROM " + table
                        + " WHERE tenant_id = :tenantId GROUP BY status")
                .param("tenantId", tenantId)
                .query((row, rowNumber) -> Map.entry(
                        row.getString("status"), row.getLong("total")))
                .list();
        var counts = new LinkedHashMap<String, Long>();
        rows.forEach(row -> counts.put(row.getKey(), row.getValue()));
        return Map.copyOf(counts);
    }
}

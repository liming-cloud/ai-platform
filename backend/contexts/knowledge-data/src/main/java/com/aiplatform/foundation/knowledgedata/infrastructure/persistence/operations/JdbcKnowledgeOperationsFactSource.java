package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.operations;

import com.aiplatform.foundation.knowledgedata.application.operations.dto.KnowledgeOperationsFacts;
import com.aiplatform.foundation.knowledgedata.application.operations.facade.KnowledgeOperationsFactSource;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 仅聚合知识资源状态列的 PostgreSQL 事实源。 */
public final class JdbcKnowledgeOperationsFactSource implements KnowledgeOperationsFactSource {

    private final JdbcClient jdbc;

    public JdbcKnowledgeOperationsFactSource(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC 客户端不能为空");
    }

    @Override
    public KnowledgeOperationsFacts platformSummary() {
        return summarize(null);
    }

    @Override
    public KnowledgeOperationsFacts tenantSummary(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        return summarize(tenantId.value());
    }

    private KnowledgeOperationsFacts summarize(Long tenantId) {
        return new KnowledgeOperationsFacts(
                grouped("knowledge_data.knowledge_space", tenantId),
                groupedUnion(tenantId),
                grouped("knowledge_data.index_version", tenantId),
                grouped("knowledge_data.index_build_job", tenantId));
    }

    private Map<String, Long> grouped(String table, Long tenantId) {
        var sql = "SELECT status, COUNT(*) total FROM " + table
                + (tenantId == null ? "" : " WHERE tenant_id = :tenantId")
                + " GROUP BY status";
        var request = jdbc.sql(sql);
        if (tenantId != null) {
            request = request.param("tenantId", tenantId);
        }
        return toMap(request.query((row, rowNumber) -> Map.entry(
                        row.getString("status"), row.getLong("total")))
                .list());
    }

    private Map<String, Long> groupedUnion(Long tenantId) {
        var filter = tenantId == null ? "" : " WHERE tenant_id = :tenantId";
        var request = jdbc.sql("""
                SELECT status, COUNT(*) total
                FROM (
                    SELECT status FROM knowledge_data.text_data_source%s
                    UNION ALL
                    SELECT status FROM knowledge_data.governed_data_source%s
                ) sources
                GROUP BY status
                """.formatted(filter, filter));
        if (tenantId != null) {
            request = request.param("tenantId", tenantId);
        }
        return toMap(request.query((row, rowNumber) -> Map.entry(
                        row.getString("status"), row.getLong("total")))
                .list());
    }

    private static Map<String, Long> toMap(Iterable<Map.Entry<String, Long>> rows) {
        var counts = new LinkedHashMap<String, Long>();
        rows.forEach(row -> counts.put(row.getKey(), row.getValue()));
        return Map.copyOf(counts);
    }
}

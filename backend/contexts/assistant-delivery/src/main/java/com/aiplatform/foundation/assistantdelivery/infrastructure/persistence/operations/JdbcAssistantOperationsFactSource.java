package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.operations;

import com.aiplatform.foundation.assistantdelivery.application.operations.dto.AssistantOperationsFacts;
import com.aiplatform.foundation.assistantdelivery.application.operations.facade.AssistantOperationsFactSource;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 只聚合助手状态列的 PostgreSQL 事实源。 */
public final class JdbcAssistantOperationsFactSource implements AssistantOperationsFactSource {

    private final JdbcClient jdbc;

    public JdbcAssistantOperationsFactSource(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC 客户端不能为空");
    }

    @Override
    public AssistantOperationsFacts tenantSummary(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        var value = tenantId.value();
        var assistants = jdbc.sql("SELECT COUNT(*) FROM assistant_delivery.assistant WHERE tenant_id = :tenantId")
                .param("tenantId", value)
                .query(Long.class)
                .single();
        return new AssistantOperationsFacts(
                assistants,
                grouped("assistant_delivery.assistant_version", value),
                grouped("assistant_delivery.assistant_test_run", value));
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

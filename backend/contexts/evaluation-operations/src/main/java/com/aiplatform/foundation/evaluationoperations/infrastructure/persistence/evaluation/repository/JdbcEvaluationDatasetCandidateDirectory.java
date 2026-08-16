package com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.dto.EvaluationDatasetCandidate;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetCandidateDirectory;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 使用 PostgreSQL 分页读取已发布数据集，避免私有内容引用进入接口层。 */
public final class JdbcEvaluationDatasetCandidateDirectory implements EvaluationDatasetCandidateDirectory {

    private final JdbcClient jdbc;

    public JdbcEvaluationDatasetCandidateDirectory(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC 客户端不能为空");
    }

    @Override
    public PageResult<EvaluationDatasetCandidate> pagePublished(TenantId tenantId, PageQuery page) {
        var total = jdbc.sql("""
                        SELECT COUNT(*) FROM evaluation_operations.evaluation_dataset
                        WHERE tenant_id = :tenantId AND status = 'PUBLISHED'
                        """)
                .param("tenantId", tenantId.value())
                .query(Long.class)
                .single();
        var items = jdbc.sql("""
                        SELECT id, entry_count, classification, created_at
                        FROM evaluation_operations.evaluation_dataset
                        WHERE tenant_id = :tenantId AND status = 'PUBLISHED'
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("tenantId", tenantId.value())
                .param("limit", page.pageSize())
                .param("offset", page.offset())
                .query((row, rowNumber) -> new EvaluationDatasetCandidate(
                        Long.toString(row.getLong("id")),
                        row.getInt("entry_count"),
                        row.getString("classification"),
                        row.getObject("created_at", java.time.OffsetDateTime.class).toInstant()))
                .list();
        return PageResult.of(items, page, total);
    }
}

package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.repository;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.CandidateCollection;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.CitationValidation;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.QueryDiagnosticFact;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.RetrievalAuthorization;
import com.aiplatform.foundation.trustedquery.application.diagnostic.port.out.QueryDiagnosticFactLookup;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryDiagnosticFactMapper;
import java.util.Objects;
import java.util.Optional;

/**
 * 将 PostgreSQL 的查询诊断行映射为应用层最小事实。
 */
public final class PostgresQueryDiagnosticFactLookup implements QueryDiagnosticFactLookup {

    private final QueryDiagnosticFactMapper mapper;

    public PostgresQueryDiagnosticFactLookup(QueryDiagnosticFactMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "查询诊断映射器不能为空");
    }

    @Override
    public Optional<QueryDiagnosticFact> find(TenantId tenantId, QueryRunId queryRunId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(queryRunId, "查询运行标识不能为空");
        var row = mapper.find(tenantId.value(), queryRunId.value());
        return row == null ? Optional.empty() : Optional.of(new QueryDiagnosticFact(
                CandidateCollection.valueOf(row.getCandidateCollection()),
                row.getCandidateCount(),
                RetrievalAuthorization.valueOf(row.getRetrievalAuthorization()),
                row.getChatModelRevision(),
                row.getEmbeddingModelRevision(),
                CitationValidation.valueOf(row.getCitationValidation())));
    }
}

package com.aiplatform.foundation.trustedquery.application.diagnostic.service;

import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.QueryDiagnosticView;
import com.aiplatform.foundation.trustedquery.application.diagnostic.exception.QueryDiagnosticPermissionDeniedException;
import com.aiplatform.foundation.trustedquery.application.diagnostic.port.out.QueryDiagnosticFactLookup;
import com.aiplatform.foundation.trustedquery.application.diagnostic.query.GetQueryDiagnosticQuery;
import com.aiplatform.foundation.trustedquery.application.run.exception.QueryRunNotFoundException;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import java.util.Objects;

/**
 * 读取已经固化的查询诊断事实，并在读取前收口审计权限与租户边界。
 */
public final class QueryDiagnosticService {

    private static final PermissionCode AUDIT_READ = new PermissionCode("audit:read");

    private final QueryRunRepository runs;
    private final QueryDiagnosticFactLookup facts;

    public QueryDiagnosticService(
            QueryRunRepository runs,
            QueryDiagnosticFactLookup facts) {
        this.runs = Objects.requireNonNull(runs, "查询运行仓储不能为空");
        this.facts = Objects.requireNonNull(facts, "查询诊断事实源不能为空");
    }

    public QueryDiagnosticView get(GetQueryDiagnosticQuery query) {
        Objects.requireNonNull(query, "查询诊断读取请求不能为空");
        if (!query.context().hasPermission(AUDIT_READ)) {
            throw new QueryDiagnosticPermissionDeniedException();
        }
        var queryRunId = QueryRunId.parse(query.queryRunId());
        var queryRun = runs.find(query.context().tenantId(), queryRunId)
                .orElseThrow(QueryRunNotFoundException::new);
        var diagnostic = facts.find(query.context().tenantId(), queryRunId)
                .orElseThrow(QueryRunNotFoundException::new);
        var usage = queryRun.usage();
        return new QueryDiagnosticView(
                queryRun.id().toString(),
                queryRun.status().name(),
                diagnostic.candidateCollection(),
                diagnostic.candidateCount(),
                diagnostic.retrievalAuthorization(),
                diagnostic.chatModelRevision(),
                diagnostic.embeddingModelRevision(),
                diagnostic.citationValidation(),
                queryRun.citations().size(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens());
    }
}

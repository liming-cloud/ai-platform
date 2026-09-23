package com.aiplatform.foundation.trustedquery.application.diagnostic.dto;

import com.aiplatform.foundation.assistantdelivery.application.answer.facade.ModelInvocationTrace;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRun;
import java.util.Objects;

/**
 * 同一事务提交的查询终态与内部诊断事实。
 */
public record QueryRunTerminalCommit(
        QueryRun queryRun,
        long expectedVersion,
        QueryDiagnosticFact diagnostic,
        ModelInvocationTrace modelInvocationTrace) {

    public QueryRunTerminalCommit(
            QueryRun queryRun,
            long expectedVersion,
            QueryDiagnosticFact diagnostic) {
        this(queryRun, expectedVersion, diagnostic, null);
    }

    public QueryRunTerminalCommit {
        queryRun = Objects.requireNonNull(queryRun, "查询终态不能为空");
        diagnostic = Objects.requireNonNull(diagnostic, "查询诊断事实不能为空");
        if (queryRun.status() == com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunStatus.RUNNING
                || expectedVersion < 0) {
            throw new IllegalArgumentException("查询终态提交不正确");
        }
    }
}

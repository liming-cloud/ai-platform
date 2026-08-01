package com.aiplatform.foundation.trustedquery.interfaces.web.query.controller;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.QueryDiagnosticView;
import com.aiplatform.foundation.trustedquery.application.diagnostic.query.GetQueryDiagnosticQuery;
import com.aiplatform.foundation.trustedquery.application.diagnostic.service.QueryDiagnosticService;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅供审计角色读取查询最小诊断事实的在线入口。
 */
@RestController
@RequestMapping("/api/v1/query-runs")
@ConditionalOnProperty(name = "foundation.query.enabled", havingValue = "true")
public class QueryDiagnosticController {

    private final QueryDiagnosticService diagnostics;

    public QueryDiagnosticController(QueryDiagnosticService diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics, "查询诊断用例不能为空");
    }

    /**
     * 身份和审计权限由令牌解析的可信上下文决定，路径只标识待读取的查询运行。
     */
    @GetMapping("/{queryRunId}/diagnostic")
    public QueryDiagnosticView get(
            TrustedRequestContext context,
            @PathVariable String queryRunId) {
        return diagnostics.get(new GetQueryDiagnosticQuery(context, queryRunId));
    }
}

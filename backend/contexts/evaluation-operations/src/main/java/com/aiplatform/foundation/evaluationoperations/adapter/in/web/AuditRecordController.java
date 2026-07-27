package com.aiplatform.foundation.evaluationoperations.adapter.in.web;

import com.aiplatform.foundation.evaluationoperations.api.OperationsQueryUseCase;
import com.aiplatform.foundation.evaluationoperations.api.model.ListQueryAuditRecordsQuery;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryAuditRecordPage;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前租户脱敏查询审计入口。
 */
@RestController
@RequestMapping("/api/v1/audit-records")
@ConditionalOnBean(OperationsQueryUseCase.class)
public class AuditRecordController {

    private final OperationsQueryUseCase operations;

    public AuditRecordController(OperationsQueryUseCase operations) {
        this.operations = Objects.requireNonNull(operations, "运营查询用例不能为空");
    }

    @GetMapping
    public QueryAuditRecordPage list(
            TrustedRequestContext context,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return operations.audit(
                new ListQueryAuditRecordsQuery(context, cursor, limit));
    }
}

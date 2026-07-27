package com.aiplatform.foundation.evaluationoperations.interfaces.web.operations.controller;

import com.aiplatform.foundation.evaluationoperations.application.operations.service.OperationsQueryService;
import com.aiplatform.foundation.evaluationoperations.application.operations.query.ListQueryAuditRecordsQuery;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.QueryAuditRecordView;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前租户脱敏查询审计入口。
 */
@RestController
@RequestMapping("/api/v1/audit-records")
@ConditionalOnProperty(
        name = "foundation.evaluation-operations.enabled",
        havingValue = "true")
public class AuditRecordController {

    private final OperationsQueryService operations;

    public AuditRecordController(OperationsQueryService operations) {
        this.operations = Objects.requireNonNull(operations, "运营查询用例不能为空");
    }

    @GetMapping
    public PageResult<QueryAuditRecordView> list(
            TrustedRequestContext context,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        return operations.audit(
                new ListQueryAuditRecordsQuery(
                        context,
                        new PageQuery(pageNumber, pageSize)));
    }
}

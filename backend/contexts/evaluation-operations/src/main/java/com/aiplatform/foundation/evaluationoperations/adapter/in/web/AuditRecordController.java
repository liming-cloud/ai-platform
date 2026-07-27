package com.aiplatform.foundation.evaluationoperations.adapter.in.web;

import com.aiplatform.foundation.evaluationoperations.api.OperationsQueryUseCase;
import com.aiplatform.foundation.evaluationoperations.api.model.ListQueryAuditRecordsQuery;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryAuditRecordView;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
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

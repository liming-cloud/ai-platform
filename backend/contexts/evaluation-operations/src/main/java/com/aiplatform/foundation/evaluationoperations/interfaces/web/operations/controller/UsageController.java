package com.aiplatform.foundation.evaluationoperations.interfaces.web.operations.controller;

import com.aiplatform.foundation.evaluationoperations.application.operations.service.OperationsQueryService;
import com.aiplatform.foundation.evaluationoperations.application.operations.query.QueryUsageQuery;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.QueryUsageView;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Instant;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前租户可信查询用量和配额入口。
 */
@RestController
@RequestMapping("/api/v1/usage")
@ConditionalOnProperty(
        name = "foundation.evaluation-operations.enabled",
        havingValue = "true")
public class UsageController {

    private final OperationsQueryService operations;

    public UsageController(OperationsQueryService operations) {
        this.operations = Objects.requireNonNull(operations, "运营查询用例不能为空");
    }

    @GetMapping
    public QueryUsageView get(
            TrustedRequestContext context,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return operations.usage(new QueryUsageQuery(context, from, to));
    }
}

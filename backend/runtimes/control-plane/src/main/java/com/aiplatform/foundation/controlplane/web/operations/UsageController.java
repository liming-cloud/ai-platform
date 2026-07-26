package com.aiplatform.foundation.controlplane.web.operations;

import com.aiplatform.foundation.evaluationoperations.api.OperationsQueryUseCase;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryUsageQuery;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryUsageView;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.time.Instant;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前租户可信查询用量和配额入口。
 */
@RestController
@RequestMapping("/api/v1/usage")
@ConditionalOnBean(OperationsQueryUseCase.class)
public class UsageController {

    private final OperationsQueryUseCase operations;

    public UsageController(OperationsQueryUseCase operations) {
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

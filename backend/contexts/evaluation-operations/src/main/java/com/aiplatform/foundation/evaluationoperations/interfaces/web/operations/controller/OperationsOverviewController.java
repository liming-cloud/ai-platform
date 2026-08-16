package com.aiplatform.foundation.evaluationoperations.interfaces.web.operations.controller;

import com.aiplatform.foundation.evaluationoperations.application.operations.dto.OperationsRange;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.PlatformOperationsOverviewView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.PlatformTenantOperationsSummaryView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.TenantOperationsOverviewView;
import com.aiplatform.foundation.evaluationoperations.application.operations.service.OperationsOverviewService;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 平台与租户两级运营总览 HTTP 入口。 */
@RestController
@ConditionalOnBean(OperationsOverviewService.class)
public final class OperationsOverviewController {

    private final OperationsOverviewService service;

    public OperationsOverviewController(OperationsOverviewService service) {
        this.service = Objects.requireNonNull(service, "运营总览服务不能为空");
    }

    @GetMapping("/api/v1/platform/operations/overview")
    public PlatformOperationsOverviewView platformOverview(
            PlatformRequestContext context,
            @RequestParam(defaultValue = "DAY_7") OperationsRange range) {
        return service.platformOverview(context, range);
    }

    @GetMapping("/api/v1/platform/operations/tenants")
    public PageResult<PlatformTenantOperationsSummaryView> platformTenants(
            PlatformRequestContext context,
            @RequestParam(defaultValue = "DAY_7") OperationsRange range,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return service.tenantSummaries(context, range, new PageQuery(page, size));
    }

    @GetMapping("/api/v1/operations/overview")
    public TenantOperationsOverviewView tenantOverview(
            TrustedRequestContext context,
            @RequestParam(defaultValue = "DAY_7") OperationsRange range) {
        return service.tenantOverview(context, range);
    }
}

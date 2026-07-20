package com.aiplatform.summary.presentation;

import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.web.response.ApiResponse;
import com.aiplatform.summary.application.SummaryOpsApplicationService;
import com.aiplatform.summary.application.result.PlatformOverviewResult;
import com.aiplatform.summary.application.result.TenantWorkbenchResult;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/summary")
public class SummaryOpsController {
    private final SummaryOpsApplicationService service;

    public SummaryOpsController(SummaryOpsApplicationService service) {
        this.service = Objects.requireNonNull(service, "SummaryOps 应用服务不能为空");
    }

    @GetMapping("/platform-overview")
    public ApiResponse<PlatformOverviewResult> platformOverview(
            @RequestParam(required = false) String tenantStatus,
            @RequestParam(required = false) String metricScope
    ) {
        return ApiResponse.success(service.platformOverview(
                TenantContext.current(),
                PrincipalContext.currentActorId(),
                tenantStatus,
                metricScope,
                TraceContext.currentTraceId()
        ));
    }

    @GetMapping("/tenant-workbench")
    public ApiResponse<TenantWorkbenchResult> tenantWorkbench() {
        return ApiResponse.success(service.tenantWorkbench(
                TenantContext.current(),
                PrincipalContext.currentActorId(),
                TraceContext.currentTraceId()
        ));
    }
}

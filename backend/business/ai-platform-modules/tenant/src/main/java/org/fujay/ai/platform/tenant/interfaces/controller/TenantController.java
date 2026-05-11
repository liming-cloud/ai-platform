package org.fujay.ai.platform.tenant.interfaces.controller;

import org.fujay.ai.platform.shared.response.ApiResponse;
import org.fujay.ai.platform.shared.trace.TraceContext;
import org.fujay.ai.platform.tenant.application.service.TenantAccessApplicationService;
import org.fujay.ai.platform.tenant.interfaces.assembler.TenantAccessAssembler;
import org.fujay.ai.platform.tenant.interfaces.response.TenantResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantAccessApplicationService applicationService;
    private final TenantAccessAssembler assembler;

    public TenantController(TenantAccessApplicationService applicationService, TenantAccessAssembler assembler) {
        this.applicationService = applicationService;
        this.assembler = assembler;
    }

    @GetMapping("/current")
    public ApiResponse<TenantResponse> current() {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toTenantResponse(applicationService.currentTenant()));
    }

    @GetMapping("/{tenantId}")
    public ApiResponse<TenantResponse> byId(@PathVariable("tenantId") String tenantId) {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toTenantResponse(applicationService.tenantById(tenantId)));
    }
}

package org.fujay.ai.platform.tenant.interfaces.controller;

import org.fujay.ai.platform.shared.response.ApiResponse;
import org.fujay.ai.platform.shared.trace.TraceContext;
import org.fujay.ai.platform.tenant.application.service.TenantAuthApplicationService;
import org.fujay.ai.platform.tenant.interfaces.assembler.TenantAccessAssembler;
import org.fujay.ai.platform.tenant.interfaces.response.TenantResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantAuthApplicationService authService;
    private final TenantAccessAssembler assembler;

    public TenantController(TenantAuthApplicationService authService, TenantAccessAssembler assembler) {
        this.authService = authService;
        this.assembler = assembler;
    }

    @GetMapping("/current")
    public ApiResponse<TenantResponse> current() {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toTenantResponse(authService.currentTenant()));
    }

    @GetMapping("/{tenantId}")
    public ApiResponse<TenantResponse> byId(@PathVariable("tenantId") String tenantId) {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toTenantResponse(authService.tenantById(tenantId)));
    }
}

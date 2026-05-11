package org.fujay.ai.platform.tenant.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import java.util.List;
import org.fujay.ai.platform.shared.response.ApiResponse;
import org.fujay.ai.platform.shared.trace.TraceContext;
import org.fujay.ai.platform.tenant.application.command.CreateTenantCommand;
import org.fujay.ai.platform.tenant.application.command.UpdateTenantCommand;
import org.fujay.ai.platform.tenant.application.service.TenantAccessApplicationService;
import org.fujay.ai.platform.tenant.interfaces.assembler.TenantAccessAssembler;
import org.fujay.ai.platform.tenant.interfaces.request.TenantCreateRequest;
import org.fujay.ai.platform.tenant.interfaces.request.TenantStatusRequest;
import org.fujay.ai.platform.tenant.interfaces.request.TenantUpdateRequest;
import org.fujay.ai.platform.tenant.interfaces.response.TenantResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tenants")
public class AdminTenantController {

    private final TenantAccessApplicationService applicationService;
    private final TenantAccessAssembler assembler;

    public AdminTenantController(TenantAccessApplicationService applicationService, TenantAccessAssembler assembler) {
        this.applicationService = applicationService;
        this.assembler = assembler;
    }

    @GetMapping
    @SaCheckPermission("platform:tenant:read")
    public ApiResponse<List<TenantResponse>> listTenants() {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                applicationService.listTenantsForAdmin().stream().map(assembler::toTenantResponse).toList());
    }

    @PostMapping
    @SaCheckPermission("platform:tenant:write")
    public ApiResponse<TenantResponse> createTenant(@RequestBody TenantCreateRequest request) {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toTenantResponse(applicationService.createTenantForAdmin(new CreateTenantCommand(
                        request.tenantCode(),
                        request.tenantName(),
                        request.adminUsername(),
                        request.adminDisplayName(),
                        request.adminPassword(),
                        request.status()))));
    }

    @PutMapping("/{tenantId}")
    @SaCheckPermission("platform:tenant:write")
    public ApiResponse<TenantResponse> updateTenant(
            @PathVariable("tenantId") String tenantId, @RequestBody TenantUpdateRequest request) {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toTenantResponse(applicationService.updateTenantForAdmin(
                        tenantId, new UpdateTenantCommand(request.tenantName(), request.status()))));
    }

    @PatchMapping("/{tenantId}/status")
    @SaCheckPermission("platform:tenant:write")
    public ApiResponse<TenantResponse> updateTenantStatus(
            @PathVariable("tenantId") String tenantId, @RequestBody TenantStatusRequest request) {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toTenantResponse(applicationService.updateTenantStatusForAdmin(tenantId, request.status())));
    }
}

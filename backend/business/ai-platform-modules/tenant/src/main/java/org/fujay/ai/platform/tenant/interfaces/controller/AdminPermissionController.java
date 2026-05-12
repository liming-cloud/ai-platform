package org.fujay.ai.platform.tenant.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import java.util.List;
import org.fujay.ai.platform.shared.response.ApiResponse;
import org.fujay.ai.platform.shared.trace.TraceContext;
import org.fujay.ai.platform.tenant.application.command.SavePermissionCommand;
import org.fujay.ai.platform.tenant.application.service.IamAdminApplicationService;
import org.fujay.ai.platform.tenant.interfaces.assembler.TenantAccessAssembler;
import org.fujay.ai.platform.tenant.interfaces.request.PermissionSaveRequest;
import org.fujay.ai.platform.tenant.interfaces.response.RoleManagementResponse.PermissionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/permissions")
public class AdminPermissionController {

    private final IamAdminApplicationService iamAdminService;
    private final TenantAccessAssembler assembler;

    public AdminPermissionController(IamAdminApplicationService iamAdminService, TenantAccessAssembler assembler) {
        this.iamAdminService = iamAdminService;
        this.assembler = assembler;
    }

    @GetMapping
    @SaCheckPermission("rbac:permission:read")
    public ApiResponse<List<PermissionResponse>> listPermissions() {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                iamAdminService.listPermissionsForAdmin().stream().map(assembler::toPermissionResponse).toList());
    }

    @PostMapping
    @SaCheckPermission("rbac:permission:write")
    public ApiResponse<PermissionResponse> savePermission(@RequestBody PermissionSaveRequest request) {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toPermissionResponse(iamAdminService.savePermissionForAdmin(new SavePermissionCommand(
                        request.permissionId(), request.permissionCode(), request.permissionName(), request.status()))));
    }
}

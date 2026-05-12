package org.fujay.ai.platform.tenant.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.fujay.ai.platform.shared.response.ApiResponse;
import org.fujay.ai.platform.shared.trace.TraceContext;
import org.fujay.ai.platform.tenant.application.command.SaveRoleCommand;
import org.fujay.ai.platform.tenant.application.service.IamAdminApplicationService;
import org.fujay.ai.platform.tenant.interfaces.assembler.TenantAccessAssembler;
import org.fujay.ai.platform.tenant.interfaces.request.RoleBindingRequest;
import org.fujay.ai.platform.tenant.interfaces.request.RoleSaveRequest;
import org.fujay.ai.platform.tenant.interfaces.response.RoleManagementResponse;
import org.fujay.ai.platform.tenant.interfaces.response.RoleManagementResponse.RoleAccessResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/roles")
public class AdminRoleController {

    private final IamAdminApplicationService iamAdminService;
    private final TenantAccessAssembler assembler;

    public AdminRoleController(IamAdminApplicationService iamAdminService, TenantAccessAssembler assembler) {
        this.iamAdminService = iamAdminService;
        this.assembler = assembler;
    }

    @GetMapping
    @SaCheckPermission("rbac:role:read")
    public ApiResponse<RoleManagementResponse> roleManagement() {
        return ApiResponse.success(
                TraceContext.currentTraceId(), assembler.toRoleManagementResponse(iamAdminService.roleManagementForAdmin()));
    }

    @PostMapping
    @SaCheckPermission("rbac:role:write")
    public ApiResponse<RoleAccessResponse> saveRole(@RequestBody RoleSaveRequest request) {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toRoleAccessResponse(iamAdminService.saveRoleForAdmin(new SaveRoleCommand(
                        request.roleId(), request.roleCode(), request.roleName(), request.status()))));
    }

    @PutMapping("/{roleId}/bindings")
    @SaCheckPermission("rbac:role:write")
    public ApiResponse<RoleAccessResponse> updateRoleBindings(
            @PathVariable("roleId") String roleId, @RequestBody RoleBindingRequest request) {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toRoleAccessResponse(iamAdminService.updateRoleBindingsForAdmin(
                        roleId, request.permissionCodes(), request.menuCodes())));
    }
}

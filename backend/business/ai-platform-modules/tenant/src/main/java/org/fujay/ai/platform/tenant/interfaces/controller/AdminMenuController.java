package org.fujay.ai.platform.tenant.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import java.util.List;
import org.fujay.ai.platform.shared.response.ApiResponse;
import org.fujay.ai.platform.shared.trace.TraceContext;
import org.fujay.ai.platform.tenant.application.command.SaveMenuCommand;
import org.fujay.ai.platform.tenant.application.service.IamAdminApplicationService;
import org.fujay.ai.platform.tenant.interfaces.assembler.TenantAccessAssembler;
import org.fujay.ai.platform.tenant.interfaces.request.MenuSaveRequest;
import org.fujay.ai.platform.tenant.interfaces.response.RoleManagementResponse.MenuResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/menus")
public class AdminMenuController {

    private final IamAdminApplicationService iamAdminService;
    private final TenantAccessAssembler assembler;

    public AdminMenuController(IamAdminApplicationService iamAdminService, TenantAccessAssembler assembler) {
        this.iamAdminService = iamAdminService;
        this.assembler = assembler;
    }

    @GetMapping
    @SaCheckPermission("rbac:menu:read")
    public ApiResponse<List<MenuResponse>> listMenus() {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                iamAdminService.listMenusForAdmin().stream().map(assembler::toMenuResponse).toList());
    }

    @PostMapping
    @SaCheckPermission("rbac:menu:write")
    public ApiResponse<MenuResponse> saveMenu(@RequestBody MenuSaveRequest request) {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toMenuResponse(iamAdminService.saveMenuForAdmin(new SaveMenuCommand(
                        request.menuId(),
                        request.menuCode(),
                        request.menuName(),
                        request.parentCode(),
                        request.routePath(),
                        request.componentKey(),
                        request.platformScope(),
                        request.sortOrder(),
                        request.status()))));
    }
}

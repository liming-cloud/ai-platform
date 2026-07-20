package com.aiplatform.identity.presentation.controller;

import com.aiplatform.identity.application.IamConfigurationQueryApplicationService;
import com.aiplatform.identity.presentation.dto.IamMenuConfigurationResponse;
import com.aiplatform.identity.presentation.dto.IamPermissionResponse;
import com.aiplatform.identity.presentation.dto.IamRoleResponse;
import com.aiplatform.identity.presentation.dto.IamTenantFeatureResponse;
import com.aiplatform.identity.presentation.dto.IamUserConfigurationResponse;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.web.response.ApiResponse;
import com.aiplatform.shared.web.response.PageResponse;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * IAM 配置只读 HTTP 适配器，供系统设置读取菜单、租户功能、角色与授权矩阵。
 */
@RestController
@RequestMapping("/api/v1/iam")
public class IamConfigurationController {
    private final IamConfigurationQueryApplicationService service;

    public IamConfigurationController(IamConfigurationQueryApplicationService service) {
        this.service = Objects.requireNonNull(service, "IAM 配置查询应用服务不能为空");
    }

    @GetMapping("/menus")
    public ApiResponse<List<IamMenuConfigurationResponse>> listMenus() {
        return ApiResponse.success(service.listMenus(TenantContext.current())
                .stream()
                .map(IamMenuConfigurationResponse::from)
                .toList());
    }

    @GetMapping("/tenant-features")
    public ApiResponse<List<IamTenantFeatureResponse>> listTenantFeatures() {
        return ApiResponse.success(service.listTenantFeatures(TenantContext.current())
                .stream()
                .map(IamTenantFeatureResponse::from)
                .toList());
    }

    @GetMapping("/roles")
    public ApiResponse<List<IamRoleResponse>> listRoles() {
        return ApiResponse.success(service.listRoles(TenantContext.current())
                .stream()
                .map(IamRoleResponse::from)
                .toList());
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<IamUserConfigurationResponse>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        var result = service.listUsers(TenantContext.current(), page, pageSize);
        return ApiResponse.success(new PageResponse<>(
                result.items().stream().map(IamUserConfigurationResponse::from).toList(),
                result.page(),
                result.pageSize(),
                result.total(),
                result.totalPages()
        ));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<IamPermissionResponse>> listPermissions() {
        return ApiResponse.success(service.listPermissions(TenantContext.current())
                .stream()
                .map(IamPermissionResponse::from)
                .toList());
    }

    @GetMapping("/roles/{roleId}/menus")
    public ApiResponse<List<IamMenuConfigurationResponse>> listRoleMenus(@PathVariable String roleId) {
        return ApiResponse.success(service.listRoleMenus(TenantContext.current(), roleId)
                .stream()
                .map(IamMenuConfigurationResponse::from)
                .toList());
    }

    @GetMapping("/roles/{roleId}/permissions")
    public ApiResponse<List<IamPermissionResponse>> listRolePermissions(@PathVariable String roleId) {
        return ApiResponse.success(service.listRolePermissions(TenantContext.current(), roleId)
                .stream()
                .map(IamPermissionResponse::from)
                .toList());
    }
}

package com.aiplatform.identity.presentation.controller;

import com.aiplatform.identity.application.IamConfigurationCommandApplicationService;
import com.aiplatform.identity.application.command.CreateIamMenuCommand;
import com.aiplatform.identity.application.command.ReplaceIamMenuFeaturesCommand;
import com.aiplatform.identity.application.command.ReplaceIamRoleMenusCommand;
import com.aiplatform.identity.application.command.ReplaceIamRolePermissionsCommand;
import com.aiplatform.identity.application.command.UpdateIamMenuCommand;
import com.aiplatform.identity.application.command.UpdateIamRoleDefaultMenuCommand;
import com.aiplatform.identity.application.command.UpdateIamTenantFeatureCommand;
import com.aiplatform.identity.presentation.dto.IamConfigurationMutationResponse;
import com.aiplatform.identity.presentation.dto.IamMenuCreateRequest;
import com.aiplatform.identity.presentation.dto.IamMenuFeaturesReplaceRequest;
import com.aiplatform.identity.presentation.dto.IamMenuUpdateRequest;
import com.aiplatform.identity.presentation.dto.IamRoleDefaultMenuUpdateRequest;
import com.aiplatform.identity.presentation.dto.IamRoleMenusReplaceRequest;
import com.aiplatform.identity.presentation.dto.IamRolePermissionsReplaceRequest;
import com.aiplatform.identity.presentation.dto.IamTenantFeatureUpdateRequest;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.web.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IAM 配置命令 HTTP 适配器，负责把系统设置保存请求转换为应用层命令。
 */
@RestController
@RequestMapping("/api/v1/iam")
public class IamConfigurationCommandController {
    private final IamConfigurationCommandApplicationService service;

    public IamConfigurationCommandController(IamConfigurationCommandApplicationService service) {
        this.service = Objects.requireNonNull(service, "IAM 配置命令应用服务不能为空");
    }

    @PostMapping("/menus")
    public ApiResponse<IamConfigurationMutationResponse> createMenu(
            @Valid @RequestBody IamMenuCreateRequest request
    ) {
        String menuId = service.createMenu(new CreateIamMenuCommand(
                TenantContext.current(),
                PrincipalContext.requireCurrent().actorId(),
                TraceContext.currentTraceId(),
                request.code(),
                request.name(),
                request.scope(),
                request.path(),
                request.routeName(),
                request.componentKey(),
                request.icon(),
                request.requiredPermissionCode(),
                request.sort(),
                request.status(),
                request.featureCodes()
        ));
        return ApiResponse.success(IamConfigurationMutationResponse.saved(menuId));
    }

    @PatchMapping("/menus/{menuId}")
    public ApiResponse<IamConfigurationMutationResponse> updateMenu(
            @PathVariable String menuId,
            @Valid @RequestBody IamMenuUpdateRequest request
    ) {
        service.updateMenu(new UpdateIamMenuCommand(
                TenantContext.current(),
                PrincipalContext.requireCurrent().actorId(),
                TraceContext.currentTraceId(),
                menuId,
                request.name(),
                request.path(),
                request.routeName(),
                request.componentKey(),
                request.icon(),
                request.requiredPermissionCode(),
                request.sort(),
                request.status()
        ));
        return ApiResponse.success(IamConfigurationMutationResponse.saved(menuId));
    }

    @PutMapping("/menus/{menuId}/features")
    public ApiResponse<IamConfigurationMutationResponse> replaceMenuFeatures(
            @PathVariable String menuId,
            @Valid @RequestBody IamMenuFeaturesReplaceRequest request
    ) {
        service.replaceMenuFeatures(new ReplaceIamMenuFeaturesCommand(
                TenantContext.current(),
                PrincipalContext.requireCurrent().actorId(),
                TraceContext.currentTraceId(),
                menuId,
                request.featureCodes()
        ));
        return ApiResponse.success(IamConfigurationMutationResponse.saved(menuId));
    }

    @PutMapping("/tenant-features/{featureCode}")
    public ApiResponse<IamConfigurationMutationResponse> updateTenantFeature(
            @PathVariable String featureCode,
            @Valid @RequestBody IamTenantFeatureUpdateRequest request
    ) {
        service.updateTenantFeature(new UpdateIamTenantFeatureCommand(
                TenantContext.current(),
                PrincipalContext.requireCurrent().actorId(),
                TraceContext.currentTraceId(),
                featureCode,
                request.enabled(),
                request.reason()
        ));
        return ApiResponse.success(IamConfigurationMutationResponse.saved(featureCode));
    }

    @PutMapping("/roles/{roleId}/menus")
    public ApiResponse<IamConfigurationMutationResponse> replaceRoleMenus(
            @PathVariable String roleId,
            @Valid @RequestBody IamRoleMenusReplaceRequest request
    ) {
        service.replaceRoleMenus(new ReplaceIamRoleMenusCommand(
                TenantContext.current(),
                PrincipalContext.requireCurrent().actorId(),
                TraceContext.currentTraceId(),
                roleId,
                request.menuIds()
        ));
        return ApiResponse.success(IamConfigurationMutationResponse.saved(roleId));
    }

    @PutMapping("/roles/{roleId}/permissions")
    public ApiResponse<IamConfigurationMutationResponse> replaceRolePermissions(
            @PathVariable String roleId,
            @Valid @RequestBody IamRolePermissionsReplaceRequest request
    ) {
        service.replaceRolePermissions(new ReplaceIamRolePermissionsCommand(
                TenantContext.current(),
                PrincipalContext.requireCurrent().actorId(),
                TraceContext.currentTraceId(),
                roleId,
                request.permissionCodes(),
                request.reason()
        ));
        return ApiResponse.success(IamConfigurationMutationResponse.saved(roleId));
    }

    @PutMapping("/roles/{roleId}/default-menu")
    public ApiResponse<IamConfigurationMutationResponse> updateRoleDefaultMenu(
            @PathVariable String roleId,
            @Valid @RequestBody IamRoleDefaultMenuUpdateRequest request
    ) {
        service.updateRoleDefaultMenu(new UpdateIamRoleDefaultMenuCommand(
                TenantContext.current(),
                PrincipalContext.requireCurrent().actorId(),
                TraceContext.currentTraceId(),
                roleId,
                request.defaultMenuCode()
        ));
        return ApiResponse.success(IamConfigurationMutationResponse.saved(roleId));
    }
}

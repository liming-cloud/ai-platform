package com.aiplatform.identity.presentation.dto;

import com.aiplatform.identity.application.result.IamPermissionResult;

public record IamPermissionResponse(
        String permissionId,
        String code,
        String name,
        String resourceType,
        String actionCode,
        String description,
        String status
) {
    public IamPermissionResponse {
        permissionId = IdentityResponseContracts.requireText(permissionId, "权限 ID");
        code = IdentityResponseContracts.requireText(code, "权限编码");
        name = IdentityResponseContracts.requireText(name, "权限名称");
        resourceType = IdentityResponseContracts.requireText(resourceType, "资源类型");
        actionCode = IdentityResponseContracts.requireText(actionCode, "动作编码");
        description = description == null ? null : description.trim();
        status = IdentityResponseContracts.requireText(status, "权限状态");
    }

    public static IamPermissionResponse from(IamPermissionResult result) {
        IdentityResponseContracts.requireValue(result, "IAM 权限结果");
        return new IamPermissionResponse(
                result.permissionId(),
                result.code(),
                result.name(),
                result.resourceType(),
                result.actionCode(),
                result.description(),
                result.status()
        );
    }
}

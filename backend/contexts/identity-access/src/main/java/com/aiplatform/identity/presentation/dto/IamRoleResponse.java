package com.aiplatform.identity.presentation.dto;

import com.aiplatform.identity.application.result.IamRoleResult;

public record IamRoleResponse(
        String roleId,
        String code,
        String name,
        String type,
        String defaultMenuCode,
        String status,
        String description
) {
    public IamRoleResponse {
        roleId = IdentityResponseContracts.requireText(roleId, "角色 ID");
        code = IdentityResponseContracts.requireText(code, "角色编码");
        name = IdentityResponseContracts.requireText(name, "角色名称");
        type = IdentityResponseContracts.requireText(type, "角色类型");
        defaultMenuCode = defaultMenuCode == null ? null : defaultMenuCode.trim();
        status = IdentityResponseContracts.requireText(status, "角色状态");
        description = description == null ? null : description.trim();
    }

    public static IamRoleResponse from(IamRoleResult result) {
        IdentityResponseContracts.requireValue(result, "IAM 角色结果");
        return new IamRoleResponse(
                result.roleId(),
                result.code(),
                result.name(),
                result.type(),
                result.defaultMenuCode(),
                result.status(),
                result.description()
        );
    }
}

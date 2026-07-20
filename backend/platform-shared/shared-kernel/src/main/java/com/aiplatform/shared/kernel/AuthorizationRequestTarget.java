package com.aiplatform.shared.kernel;

/**
 * 服务端确认可申请的最小资源授权目标。该结构只描述可公开的资源族、资源 ID 和权限点。
 */
public record AuthorizationRequestTarget(
        String resourceType,
        String resourceId,
        String permissionCode
) implements ValueObject {
    public AuthorizationRequestTarget {
        resourceType = requireText(resourceType, "资源类型");
        resourceId = requireText(resourceId, "资源 ID");
        permissionCode = requireText(permissionCode, "权限编码");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

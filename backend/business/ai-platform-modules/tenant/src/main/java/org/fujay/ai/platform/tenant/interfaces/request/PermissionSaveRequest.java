package org.fujay.ai.platform.tenant.interfaces.request;

public record PermissionSaveRequest(String permissionId, String permissionCode, String permissionName, String status) {
}

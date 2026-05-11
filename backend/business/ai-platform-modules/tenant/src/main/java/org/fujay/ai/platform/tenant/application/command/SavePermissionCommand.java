package org.fujay.ai.platform.tenant.application.command;

public record SavePermissionCommand(String permissionId, String permissionCode, String permissionName, String status) {
}

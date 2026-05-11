package org.fujay.ai.platform.tenant.application.command;

public record SaveRoleCommand(String roleId, String roleCode, String roleName, String status) {
}

package org.fujay.ai.platform.tenant.application.command;

public record LoginCommand(String tenantCode, String username, String password) {
}

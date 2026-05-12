package org.fujay.ai.platform.tenant.application.command;

public record ChangePasswordCommand(String oldPassword, String newPassword) {
}

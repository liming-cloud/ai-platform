package org.fujay.ai.platform.tenant.interfaces.request;

public record ChangePasswordRequest(String oldPassword, String newPassword) {
}

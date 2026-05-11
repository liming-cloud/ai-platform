package org.fujay.ai.platform.tenant.interfaces.request;

public record LoginRequest(String tenantCode, String username, String password) {
}

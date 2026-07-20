package com.aiplatform.identity.presentation.dto;

public record LogoutResponse(String status) {
    public LogoutResponse {
        status = IdentityResponseContracts.requireText(status, "登出状态");
    }

    public static LogoutResponse success() {
        return new LogoutResponse("LOGGED_OUT");
    }
}

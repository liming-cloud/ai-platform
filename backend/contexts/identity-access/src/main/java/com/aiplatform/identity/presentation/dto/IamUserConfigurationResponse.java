package com.aiplatform.identity.presentation.dto;

import com.aiplatform.identity.application.result.IamUserConfigurationResult;
import java.util.List;

public record IamUserConfigurationResponse(String userId, String username, String displayName, String email, String status, List<String> roleCodes) {
    public static IamUserConfigurationResponse from(IamUserConfigurationResult result) {
        return new IamUserConfigurationResponse(result.userId(), result.username(), result.displayName(), result.email(), result.status(), result.roleCodes());
    }
}

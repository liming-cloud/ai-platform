package com.aiplatform.identity.application.result;

import java.util.List;

public record IamUserConfigurationResult(
        String userId,
        String username,
        String displayName,
        String email,
        String status,
        List<String> roleCodes
) {
}

package com.aiplatform.appstudio.presentation.request;

import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record ReplaceApplicationAccessPolicyRequest(
        Set<String> userIds,
        Set<String> roleIds,
        @NotBlank String reason
) {
}

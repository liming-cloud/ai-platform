package com.aiplatform.identity.presentation.dto;

import com.aiplatform.identity.application.result.LoginResult;
import java.time.Instant;
import java.util.Set;

/**
 * 登录接口响应 DTO，只暴露前端建立会话所需的边界字段。
 */
public record LoginResponse(
        String token,
        String userId,
        String tenantId,
        String displayName,
        Set<String> roleCodes,
        Instant tokenExpiresAt
) {
    public LoginResponse {
        token = IdentityResponseContracts.requireText(token, "访问令牌");
        userId = IdentityResponseContracts.requireText(userId, "用户 ID");
        tenantId = IdentityResponseContracts.requireText(tenantId, "租户 ID");
        displayName = IdentityResponseContracts.requireText(displayName, "用户展示名");
        roleCodes = IdentityResponseContracts.normalizeRoleCodeSet(roleCodes);
        tokenExpiresAt = IdentityResponseContracts.requireValue(tokenExpiresAt, "令牌过期时间");
    }

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.accessToken(),
                result.userId(),
                result.tenantId().value(),
                result.displayName(),
                result.roleCodes(),
                result.accessTokenExpiresAt()
        );
    }
}

package com.aiplatform.infra.satoken.codec;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import java.util.Optional;

public final class SaTokenPrincipalCodec {
    private static final String SEPARATOR = ":";
    private static final String USER_ACTOR_TYPE = "user";

    private SaTokenPrincipalCodec() {
    }

    public static String userLoginId(TenantId tenantId, String userId) {
        return loginId(tenantId, USER_ACTOR_TYPE, userId);
    }

    private static String loginId(TenantId tenantId, String actorType, String actorId) {
        return requireTenantId(tenantId).value() + SEPARATOR + requireText(actorType, "操作者类型")
                + SEPARATOR + requireText(actorId, "操作者ID");
    }

    public static String requireLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new IllegalArgumentException("Sa-Token 登录主体不能为空白");
        }
        String[] parts = loginId.split(SEPARATOR, -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new IllegalArgumentException("Sa-Token 登录主体格式非法");
        }
        return TenantId.of(parts[0]).value() + SEPARATOR
                + parts[1].trim() + SEPARATOR
                + parts[2].trim();
    }

    public static String requireLoginId(Object loginId) {
        if (!(loginId instanceof String loginIdText)) {
            throw new IllegalArgumentException("Sa-Token 登录主体类型非法");
        }
        return requireLoginId(loginIdText);
    }

    public static Optional<AuthenticatedPrincipal> decode(String loginId) {
        if (loginId == null) {
            return Optional.empty();
        }
        String[] parts = requireLoginId(loginId).split(SEPARATOR, -1);
        return Optional.of(new AuthenticatedPrincipal(TenantId.of(parts[0]), parts[1], parts[2]));
    }

    public static Optional<AuthenticatedPrincipal> decode(Object loginId) {
        if (loginId == null) {
            return Optional.empty();
        }
        return decode(requireLoginId(loginId));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static TenantId requireTenantId(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("租户 ID不能为空");
        }
        return tenantId;
    }
}

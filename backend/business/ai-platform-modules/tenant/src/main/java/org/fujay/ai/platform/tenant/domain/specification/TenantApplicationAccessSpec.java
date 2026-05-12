package org.fujay.ai.platform.tenant.domain.specification;

import java.util.List;
import java.util.Objects;
import org.fujay.ai.platform.shared.error.CommonErrorCode;
import org.fujay.ai.platform.shared.error.PlatformException;

public final class TenantApplicationAccessSpec {

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    private TenantApplicationAccessSpec() {
    }

    public static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public static String normalizeRequired(String value, String message) {
        requireNotBlank(value, message);
        return value.trim();
    }

    public static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static Long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new PlatformException(CommonErrorCode.BAD_REQUEST);
        }
    }

    public static Long parseOptionalId(String value) {
        return value == null || value.isBlank() ? null : parseId(value);
    }

    public static String defaultStatus(String status) {
        return status == null || status.isBlank() ? STATUS_ENABLED : status;
    }

    public static String normalizeStatus(String status) {
        requireNotBlank(status, "状态不能为空");
        String normalized = status.trim().toUpperCase();
        if (!STATUS_ENABLED.equals(normalized) && !STATUS_DISABLED.equals(normalized)) {
            throw new PlatformException(CommonErrorCode.BAD_REQUEST);
        }
        return normalized;
    }

    public static String normalizePlatformScope(String platformScope) {
        String normalized = normalizeRequired(platformScope, "菜单范围不能为空").toUpperCase();
        if (!"PLATFORM".equals(normalized) && !"TENANT".equals(normalized)) {
            throw new PlatformException(CommonErrorCode.BAD_REQUEST);
        }
        return normalized;
    }

    public static List<String> normalizeCodes(List<String> codes, String emptyMessage) {
        if (codes == null) {
            throw new IllegalArgumentException(emptyMessage);
        }
        List<String> normalized = codes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        return normalized;
    }
}

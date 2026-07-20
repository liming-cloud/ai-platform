package com.aiplatform.identity.presentation.dto;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 身份访问 HTTP 响应契约，保证登录态出站事实不可为空白或未归一化。
 */
final class IdentityResponseContracts {
    private IdentityResponseContracts() {
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    static Set<String> normalizeRoleCodeSet(Collection<String> values) {
        requireValue(values, "角色编码集合");
        Set<String> normalized = values.stream()
                .map(value -> requireText(value, "角色编码"))
                .collect(Collectors.toUnmodifiableSet());
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("角色编码集合不能为空");
        }
        return normalized;
    }

    static List<String> normalizeRoleCodeList(Collection<String> values) {
        requireValue(values, "角色编码列表");
        List<String> normalized = values.stream()
                .map(value -> requireText(value, "角色编码"))
                .distinct()
                .sorted()
                .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("角色编码列表不能为空");
        }
        return normalized;
    }
}

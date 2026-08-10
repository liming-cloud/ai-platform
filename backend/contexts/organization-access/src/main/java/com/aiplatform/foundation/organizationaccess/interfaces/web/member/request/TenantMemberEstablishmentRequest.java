package com.aiplatform.foundation.organizationaccess.interfaces.web.member.request;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 成员建立的严格 HTTP 请求，不接受租户、当前主体、角色、权限或会话字段。 */
public record TenantMemberEstablishmentRequest(long subjectId) {

    private static final Set<String> FIELDS = Set.of("subjectId");

    /**
     * 从严格 JSON 对象恢复目标全局主体标识。
     *
     * @throws IllegalArgumentException 当字段集合或主体标识不合法时拒绝。
     */
    public static TenantMemberEstablishmentRequest from(Map<String, Object> payload) {
        payload = Objects.requireNonNull(payload, "成员建立请求不能为空");
        if (!payload.keySet().equals(FIELDS) || !(payload.get("subjectId") instanceof String subjectId)) {
            throw new IllegalArgumentException("成员建立请求必须且只能包含主体标识");
        }
        try {
            var value = Long.parseLong(subjectId);
            if (value < 1L) {
                throw new IllegalArgumentException("主体标识必须是正整数");
            }
            return new TenantMemberEstablishmentRequest(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("主体标识必须是十进制正整数", exception);
        }
    }
}

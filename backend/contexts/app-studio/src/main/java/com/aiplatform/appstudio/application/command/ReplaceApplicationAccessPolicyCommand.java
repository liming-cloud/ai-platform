package com.aiplatform.appstudio.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.LinkedHashSet;
import java.util.Set;

public record ReplaceApplicationAccessPolicyCommand(
        TenantId tenantId,
        String actorId,
        String applicationId,
        Set<String> userIds,
        Set<String> roleIds,
        String reason
) {
    public ReplaceApplicationAccessPolicyCommand {
        if (tenantId == null) {
            throw new IllegalArgumentException("租户 ID 不能为空");
        }
        actorId = requireText(actorId, "操作者 ID");
        applicationId = requireText(applicationId, "应用 ID");
        userIds = normalizeIds(userIds, "用户 ID");
        roleIds = normalizeIds(roleIds, "角色 ID");
        reason = requireText(reason, "授权原因");
    }

    private static Set<String> normalizeIds(Set<String> values, String fieldName) {
        if (values == null) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(requireText(value, fieldName));
        }
        return Set.copyOf(normalized);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Objects;

/**
 * IAM 菜单功能绑定替换命令，使用 replace-all 语义维护菜单与 feature 的关系。
 */
public record ReplaceIamMenuFeaturesCommand(
        TenantId tenantId,
        String actorId,
        String traceId,
        String menuId,
        List<String> featureCodes
) {
    public ReplaceIamMenuFeaturesCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = UpdateIamMenuCommand.requireText(actorId, "操作者 ID");
        traceId = UpdateIamMenuCommand.requireText(traceId, "链路追踪 ID");
        menuId = UpdateIamMenuCommand.requireText(menuId, "菜单 ID");
        featureCodes = normalize(featureCodes, "功能编码");
    }

    static List<String> normalize(List<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + "列表不能为空");
        return values.stream()
                .map(value -> UpdateIamMenuCommand.requireText(value, fieldName))
                .distinct()
                .sorted()
                .toList();
    }
}

package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * IAM 租户功能更新命令，承载平台管理员对租户 feature 开关的状态化维护。
 */
public record UpdateIamTenantFeatureCommand(
        TenantId tenantId,
        String actorId,
        String traceId,
        String featureCode,
        boolean enabled,
        String reason
) {
    public UpdateIamTenantFeatureCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = UpdateIamMenuCommand.requireText(actorId, "操作者 ID");
        traceId = UpdateIamMenuCommand.requireText(traceId, "链路追踪 ID");
        featureCode = UpdateIamMenuCommand.requireText(featureCode, "功能编码");
        reason = IamChangeReason.normalize(reason, "能力变更原因");
    }
}

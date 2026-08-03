package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command;

import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.util.Objects;

/**
 * 建立新租户的应用命令。
 *
 * <p>初始管理员标识是受管业务资源目标，不是调用方登录态；调用方身份必须由
 * {@code PlatformRequestContext} 单独恢复。</p>
 */
public record TenantProvisioningCommand(
        String tenantCode,
        String displayName,
        SubjectId initialAdministratorId) {

    public TenantProvisioningCommand {
        tenantCode = Objects.requireNonNull(tenantCode, "租户编码不能为空");
        displayName = Objects.requireNonNull(displayName, "租户显示名称不能为空");
        initialAdministratorId = Objects.requireNonNull(initialAdministratorId, "初始管理员主体不能为空");
    }
}

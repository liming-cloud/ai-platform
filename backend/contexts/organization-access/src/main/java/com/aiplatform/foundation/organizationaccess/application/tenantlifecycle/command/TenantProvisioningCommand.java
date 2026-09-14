package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command;

import java.util.Objects;

/**
 * 建立新租户的应用命令。
 *
 * <p>只包含非秘密开户内容；密码以短生命周期字符数组传递，不参与记录的字符串表示。</p>
 */
public record TenantProvisioningCommand(
        String tenantCode,
        String displayName,
        String administratorLoginName,
        int knowledgeBases,
        int documentsPerKnowledgeBase,
        long bytesPerKnowledgeBase,
        long maxFileBytes,
        long maxTextBytes) {

    public TenantProvisioningCommand {
        tenantCode = Objects.requireNonNull(tenantCode, "租户编码不能为空");
        displayName = Objects.requireNonNull(displayName, "租户显示名称不能为空");
        administratorLoginName = Objects.requireNonNull(administratorLoginName, "管理员登录名不能为空");
        if (!tenantCode.matches("[a-z][a-z0-9-]{2,19}")
                || !displayName.equals(displayName.trim())
                || displayName.codePointCount(0, displayName.length()) < 2
                || displayName.codePointCount(0, displayName.length()) > 40) {
            throw new IllegalArgumentException("租户名称应为2至40字，编码应为3至20位小写字母、数字或连字符");
        }
    }
}

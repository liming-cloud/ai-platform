package com.aiplatform.appstudio.application.command;

import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Objects;

/**
 * 创建 AI 应用命令，固定要求提供模型配置与知识库绑定信息。
 */
public record CreateAiApplicationCommand(
        TenantId tenantId,
        String actorId,
        AiApplicationType applicationType,
        String name,
        String description,
        String modelConfigId,
        List<String> knowledgeBaseIds,
        String systemPrompt
) {
    public CreateAiApplicationCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        Objects.requireNonNull(applicationType, "应用类型不能为空");
        actorId = requireText(actorId, "操作者 ID");
        name = requireText(name, "应用名称");
        description = normalizeOptionalText(description);
        modelConfigId = requireText(modelConfigId, "模型配置 ID");
        knowledgeBaseIds = normalizeRequired(knowledgeBaseIds, "知识库 ID");
        systemPrompt = requireText(systemPrompt, "系统提示词");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> normalizeRequired(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return values.stream().map(value -> requireText(value, fieldName)).distinct().toList();
    }
}

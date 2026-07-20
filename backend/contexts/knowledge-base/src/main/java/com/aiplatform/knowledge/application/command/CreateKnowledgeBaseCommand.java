package com.aiplatform.knowledge.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * 创建知识库命令，携带服务端租户、认证操作者和知识库基础属性。
 */
public record CreateKnowledgeBaseCommand(
        TenantId tenantId,
        String operatorId,
        String operatorType,
        String name,
        String visibility,
        String description,
        String embeddingModelConfigId
) {
    public CreateKnowledgeBaseCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        operatorId = requireText(operatorId, "操作人 ID");
        operatorType = requireText(operatorType, "操作人类型");
        name = requireText(name, "知识库名称");
        visibility = requireText(visibility, "知识库可见性");
        description = description == null ? "" : description.trim();
        embeddingModelConfigId = requireText(embeddingModelConfigId, "知识库向量模型配置 ID");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

package com.aiplatform.knowledge.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * 知识文档解析命令，只接收服务端认证后的租户、操作人与目标文档标识。
 */
public record ParseKnowledgeDocumentCommand(
        TenantId tenantId,
        String operatorId,
        String knowledgeBaseId,
        String documentId
) {
    public ParseKnowledgeDocumentCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        operatorId = requireText(operatorId, "操作人 ID");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        documentId = requireText(documentId, "文档 ID");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

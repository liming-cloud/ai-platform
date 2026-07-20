package com.aiplatform.knowledge.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * 文档向量化索引命令，租户和操作者由服务端上下文生成。
 */
public record IndexKnowledgeDocumentCommand(
        TenantId tenantId,
        String operatorId,
        String operatorType,
        String knowledgeBaseId,
        String documentId
) {
    public IndexKnowledgeDocumentCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        operatorId = requireText(operatorId, "操作人 ID");
        operatorType = requireText(operatorType, "操作人类型");
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

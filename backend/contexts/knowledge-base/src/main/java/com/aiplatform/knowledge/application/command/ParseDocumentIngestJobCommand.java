package com.aiplatform.knowledge.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * 文档入库事件解析命令，按入库任务 ID 精确消费异步解析请求。
 */
public record ParseDocumentIngestJobCommand(
        TenantId tenantId,
        String ingestJobId,
        String knowledgeBaseId,
        String documentId
) {
    public ParseDocumentIngestJobCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        ingestJobId = requireText(ingestJobId, "入库任务 ID");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        documentId = requireText(documentId, "文档 ID");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

package com.aiplatform.knowledge.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Arrays;
import java.util.Objects;

/**
 * 知识文档上传命令，文件内容来自 multipart，租户与操作人必须来自服务端认证上下文。
 */
public record UploadKnowledgeDocumentCommand(
        TenantId tenantId,
        String operatorId,
        String knowledgeBaseId,
        String fileName,
        String contentType,
        byte[] content
) {
    public UploadKnowledgeDocumentCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        operatorId = requireText(operatorId, "操作人 ID");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        fileName = requireText(fileName, "文件名");
        contentType = requireText(contentType, "文件 MIME 类型");
        Objects.requireNonNull(content, "文件内容不能为空");
        if (content.length == 0) {
            throw new IllegalArgumentException("文件内容不能为空");
        }
        content = Arrays.copyOf(content, content.length);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

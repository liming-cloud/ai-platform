package com.aiplatform.knowledge.domain;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Arrays;

/**
 * 待写入对象存储的知识文档原始内容。
 */
public record DocumentObject(
        TenantId tenantId,
        String knowledgeBaseId,
        String documentId,
        String fileName,
        String contentType,
        String contentHash,
        byte[] content
) {
    public DocumentObject {
        tenantId = requireTenantId(tenantId);
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库ID");
        documentId = requireText(documentId, "文档ID");
        fileName = requireText(fileName, "文件名");
        contentType = requireText(contentType, "文件MIME类型");
        contentHash = requireText(contentHash, "文件内容哈希");
        content = copyContent(content, "文件内容");
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    public long fileSizeBytes() {
        return content.length;
    }

    private static TenantId requireTenantId(TenantId value) {
        if (value == null) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static byte[] copyContent(byte[] value, String fieldName) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return Arrays.copyOf(value, value.length);
    }
}

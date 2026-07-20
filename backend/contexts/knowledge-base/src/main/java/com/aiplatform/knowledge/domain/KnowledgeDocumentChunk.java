package com.aiplatform.knowledge.domain;

import com.aiplatform.shared.kernel.DomainEntity;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 知识文档切片聚合内实体，保存召回、引用、权限校验和索引版本所需的业务字段。
 */
public record KnowledgeDocumentChunk(
        TenantId tenantId,
        String chunkId,
        String knowledgeBaseId,
        String documentId,
        int chunkNo,
        String content,
        String contentHash,
        int tokenCount,
        Integer pageNo,
        int positionStart,
        int positionEnd,
        KnowledgeDocumentChunkMetadata metadata,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String operatorId
) implements DomainEntity<String> {
    public KnowledgeDocumentChunk {
        Objects.requireNonNull(tenantId, "租户不能为空");
        chunkId = requireText(chunkId, "切片 ID");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        documentId = requireText(documentId, "文档 ID");
        if (chunkNo < 1) {
            throw new IllegalArgumentException("切片序号必须为正数");
        }
        content = requireContent(content, "切片内容");
        contentHash = requireText(contentHash, "切片内容哈希");
        if (tokenCount < 0) {
            throw new IllegalArgumentException("token 数不能为负数");
        }
        if (positionStart < 0 || positionEnd < positionStart) {
            throw new IllegalArgumentException("切片位置范围非法");
        }
        Objects.requireNonNull(metadata, "切片元数据不能为空");
        status = requireText(status, "切片状态");
        Objects.requireNonNull(createdAt, "创建时间不能为空");
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        operatorId = requireText(operatorId, "操作人 ID");
    }

    @Override
    public String id() {
        return chunkId;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String requireContent(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}

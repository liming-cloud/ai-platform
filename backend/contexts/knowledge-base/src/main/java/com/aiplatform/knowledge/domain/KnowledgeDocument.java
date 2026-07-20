package com.aiplatform.knowledge.domain;

import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;

/**
 * 知识文档聚合根，跟踪解析、向量化和版本状态。
 */
public record KnowledgeDocument(
        TenantId tenantId,
        String documentId,
        String knowledgeBaseId,
        String fileName,
        String sourceType,
        String sourceUri,
        String contentHash,
        String contentType,
        long fileSizeBytes,
        int version,
        KnowledgeDocumentStatus status,
        Instant uploadedAt,
        Instant updatedAt,
        String uploadedBy
) implements AggregateRoot<String> {
    public KnowledgeDocument {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        documentId = requireText(documentId, "文档ID");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库ID");
        fileName = requireText(fileName, "文档名称");
        sourceType = requireText(sourceType, "文档来源类型");
        sourceUri = requireText(sourceUri, "文档来源地址");
        contentHash = requireText(contentHash, "文档内容哈希");
        contentType = requireText(contentType, "文档内容类型");
        if (fileSizeBytes < 0) {
            throw new IllegalArgumentException("文档大小不能为负数");
        }
        if (version < 1) {
            throw new IllegalArgumentException("文档版本必须为正数");
        }
        Objects.requireNonNull(status, "文档状态不能为空");
        Objects.requireNonNull(uploadedAt, "文档上传时间不能为空");
        Objects.requireNonNull(updatedAt, "文档更新时间不能为空");
        uploadedBy = requireText(uploadedBy, "文档上传人");
    }

    public boolean searchable() {
        return status == KnowledgeDocumentStatus.AVAILABLE;
    }

    public boolean chunkedWith(Collection<KnowledgeDocumentChunk> chunks) {
        Objects.requireNonNull(chunks, "文档切片列表不能为空");
        return (status == KnowledgeDocumentStatus.CHUNKED || status == KnowledgeDocumentStatus.AVAILABLE) && !chunks.isEmpty();
    }

    public boolean indexableWith(Collection<KnowledgeDocumentChunk> chunks) {
        Objects.requireNonNull(chunks, "文档切片列表不能为空");
        return switch (status) {
            case CHUNKED, VECTORIZING, AVAILABLE, FAILED -> !chunks.isEmpty();
            default -> false;
        };
    }

    public KnowledgeDocument markParsing(Instant updatedAt) {
        return withStatus(KnowledgeDocumentStatus.PARSING, updatedAt);
    }

    public KnowledgeDocument markChunked(Instant updatedAt) {
        return withStatus(KnowledgeDocumentStatus.CHUNKED, updatedAt);
    }

    public KnowledgeDocument markVectorizing(Instant updatedAt) {
        return withStatus(KnowledgeDocumentStatus.VECTORIZING, updatedAt);
    }

    public KnowledgeDocument markAvailable(Instant updatedAt) {
        return withStatus(KnowledgeDocumentStatus.AVAILABLE, updatedAt);
    }

    public KnowledgeDocument markFailed(Instant updatedAt) {
        return withStatus(KnowledgeDocumentStatus.FAILED, updatedAt);
    }

    public boolean cancellable() {
        return switch (status) {
            case UPLOADED, PARSING, CHUNKED, VECTORIZING -> true;
            default -> false;
        };
    }

    public KnowledgeDocument markCancelled(Instant updatedAt) {
        if (!cancellable()) {
            throw new IllegalStateException("只有非终态文档才能取消，当前状态=" + status.name());
        }
        return withStatus(KnowledgeDocumentStatus.CANCELLED, updatedAt);
    }

    public KnowledgeDocument requeueParse(Instant updatedAt) {
        if (status != KnowledgeDocumentStatus.FAILED) {
            throw new IllegalStateException("只有失败文档才能人工重新排队解析");
        }
        return withStatus(KnowledgeDocumentStatus.UPLOADED, updatedAt);
    }

    @Override
    public String id() {
        return documentId;
    }

    private KnowledgeDocument withStatus(KnowledgeDocumentStatus nextStatus, Instant updatedAt) {
        Objects.requireNonNull(nextStatus, "目标文档状态不能为空");
        Objects.requireNonNull(updatedAt, "文档状态更新时间不能为空");
        return new KnowledgeDocument(
                tenantId,
                documentId,
                knowledgeBaseId,
                fileName,
                sourceType,
                sourceUri,
                contentHash,
                contentType,
                fileSizeBytes,
                version,
                nextStatus,
                uploadedAt,
                updatedAt,
                uploadedBy
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

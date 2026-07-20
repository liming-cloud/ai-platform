package com.aiplatform.knowledge.domain.ingest.service;

import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkAclSnapshot;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkMetadata;
import com.aiplatform.knowledge.domain.port.ingest.model.KnowledgeDocumentChunkDraft;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 知识文档切片领域工厂，统一生成权限快照、内容哈希和索引版本。
 */
public class KnowledgeDocumentChunkFactory {
    private static final String CHUNK_STATUS_ACTIVE = "ACTIVE";
    private static final String DRAFT_CLASSIFICATION = "internal";
    private static final String DRAFT_VISIBILITY = "tenant";
    private static final String DRAFT_EMBEDDING_MODEL = "embedding-model-unassigned";

    public KnowledgeDocumentChunk createChunk(
            KnowledgeDocument document,
            KnowledgeDocumentChunkDraft draft,
            String chunkId,
            int chunkNo,
            String operatorId,
            String chunkStrategyVersion,
            Instant now
    ) {
        Objects.requireNonNull(document, "知识文档不能为空");
        Objects.requireNonNull(draft, "切片草稿不能为空");
        String normalizedChunkId = requireText(chunkId, "切片 ID");
        if (chunkNo < 1) {
            throw new IllegalArgumentException("切片序号必须为正数");
        }
        String normalizedOperatorId = requireText(operatorId, "操作人 ID");
        String normalizedChunkStrategyVersion = requireText(chunkStrategyVersion, "切块策略版本");
        Objects.requireNonNull(now, "切片创建时间不能为空");

        String content = draft.content();
        KnowledgeDocumentChunkMetadata metadata = new KnowledgeDocumentChunkMetadata(
                document.sourceUri(),
                document.version(),
                document.fileName(),
                null,
                null,
                DRAFT_CLASSIFICATION,
                DRAFT_VISIBILITY,
                KnowledgeDocumentChunkAclSnapshot.forDocument(document, normalizedOperatorId).tokens(),
                false,
                document.uploadedAt(),
                null,
                DRAFT_EMBEDDING_MODEL,
                normalizedChunkStrategyVersion,
                "kb-" + document.knowledgeBaseId() + "-doc-v" + document.version() + "-draft"
        );
        return new KnowledgeDocumentChunk(
                document.tenantId(),
                normalizedChunkId,
                document.knowledgeBaseId(),
                document.documentId(),
                chunkNo,
                content,
                sha256Hex(content.getBytes(StandardCharsets.UTF_8)),
                draft.tokenCount(),
                draft.pageNo(),
                draft.positionStart(),
                draft.positionEnd(),
                metadata,
                CHUNK_STATUS_ACTIVE,
                now,
                now,
                normalizedOperatorId
        );
    }

    private static String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 算法不可用", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

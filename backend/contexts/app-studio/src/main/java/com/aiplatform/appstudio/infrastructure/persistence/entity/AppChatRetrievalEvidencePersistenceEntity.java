package com.aiplatform.appstudio.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunEvidenceSnapshot;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 调试运行召回证据快照持久化实体，对应历史回放 evidence 表。
 */
@TableName("app_chat_retrieval_evidence")
public class AppChatRetrievalEvidencePersistenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("assistant_message_id")
    private Long assistantMessageId;
    @TableField("trace_id")
    private String traceId;
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;
    @TableField("document_id")
    private Long documentId;
    @TableField("chunk_id")
    private Long chunkId;
    @TableField("score")
    private BigDecimal score;
    @TableField("retrieval_signal")
    private String retrievalSignal;
    @TableField("index_version")
    private String indexVersion;
    @TableField("created_at")
    private Instant createdAt;

    public static AppChatRetrievalEvidencePersistenceEntity fromSnapshot(
            AppStudioDebugRunSnapshot snapshot,
            AppStudioDebugRunEvidenceSnapshot evidence
    ) {
        AppChatRetrievalEvidencePersistenceEntity entity = new AppChatRetrievalEvidencePersistenceEntity();
        entity.setTenantId(parseRequiredLong(snapshot.tenantId().value(), "租户 ID"));
        entity.setAssistantMessageId(parseRequiredLong(snapshot.assistantMessageId(), "助手消息 ID"));
        entity.setTraceId(snapshot.traceId());
        entity.setKnowledgeBaseId(parseRequiredLong(evidence.knowledgeBaseId(), "知识库 ID"));
        entity.setDocumentId(parseRequiredLong(evidence.documentId(), "文档 ID"));
        entity.setChunkId(parseRequiredLong(evidence.chunkId(), "切片 ID"));
        entity.setScore(BigDecimal.valueOf(evidence.score()));
        entity.setRetrievalSignal(evidence.retrievalSignal());
        entity.setIndexVersion(evidence.indexVersion());
        entity.setCreatedAt(snapshot.createdAt());
        return entity;
    }

    public AppStudioDebugRunEvidenceSnapshot toDomain() {
        return new AppStudioDebugRunEvidenceSnapshot(
                String.valueOf(knowledgeBaseId),
                String.valueOf(documentId),
                String.valueOf(chunkId),
                score == null ? 0.0d : score.doubleValue(),
                retrievalSignal,
                indexVersion
        );
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getAssistantMessageId() { return assistantMessageId; }
    public void setAssistantMessageId(Long assistantMessageId) { this.assistantMessageId = assistantMessageId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getRetrievalSignal() { return retrievalSignal; }
    public void setRetrievalSignal(String retrievalSignal) { this.retrievalSignal = retrievalSignal; }
    public String getIndexVersion() { return indexVersion; }
    public void setIndexVersion(String indexVersion) { this.indexVersion = indexVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

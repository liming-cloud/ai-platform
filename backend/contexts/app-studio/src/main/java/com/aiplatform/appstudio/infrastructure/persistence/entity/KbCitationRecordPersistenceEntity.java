package com.aiplatform.appstudio.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.Citation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * app-studio 回答引用落库实体，对齐 kb_citation_record 以支撑真实闭环核验。
 */
public class KbCitationRecordPersistenceEntity {
    private Long tenantId;
    private Long chatMessageId;
    private Long knowledgeBaseId;
    private Long documentId;
    private Long chunkId;
    private BigDecimal score;
    private String citationText;
    private Instant createdAt;

    public static List<KbCitationRecordPersistenceEntity> fromDomain(ChatMessage message) {
        return message.citations().stream()
                .map(citation -> fromCitation(message, citation))
                .toList();
    }

    private static KbCitationRecordPersistenceEntity fromCitation(ChatMessage message, Citation citation) {
        KbCitationRecordPersistenceEntity entity = new KbCitationRecordPersistenceEntity();
        entity.setTenantId(parseRequiredLong(message.tenantId().value(), "租户 ID"));
        entity.setChatMessageId(parseRequiredLong(message.messageId(), "聊天消息 ID"));
        entity.setKnowledgeBaseId(parseRequiredLong(citation.knowledgeBaseId(), "知识库 ID"));
        entity.setDocumentId(parseRequiredLong(citation.documentId(), "文档 ID"));
        entity.setChunkId(parseRequiredLong(citation.chunkId(), "切片 ID"));
        entity.setScore(BigDecimal.valueOf(citation.score()));
        entity.setCitationText(citation.fragment());
        entity.setCreatedAt(message.createdAt());
        return entity;
    }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getChatMessageId() { return chatMessageId; }
    public void setChatMessageId(Long chatMessageId) { this.chatMessageId = chatMessageId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getCitationText() { return citationText; }
    public void setCitationText(String citationText) { this.citationText = citationText; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Citation toDomain() {
        return new Citation(
                String.valueOf(knowledgeBaseId),
                String.valueOf(documentId),
                String.valueOf(chunkId),
                "document-" + documentId,
                citationText,
                "chunk-" + chunkId,
                score == null ? 0.0d : score.doubleValue()
        );
    }
}

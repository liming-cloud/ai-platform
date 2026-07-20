package com.aiplatform.knowledge.infrastructure.persistence.mapper;

import com.aiplatform.knowledge.infrastructure.persistence.entity.KbDocumentChunkPersistenceEntity;
import com.aiplatform.knowledge.infrastructure.persistence.projection.KbDocumentChunkRetrievalRow;
import com.aiplatform.knowledge.infrastructure.persistence.projection.KbKnowledgeIndexActiveRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KbDocumentChunkMapper extends BaseMapper<KbDocumentChunkPersistenceEntity> {
    Long nextChunkId();

    KbKnowledgeIndexActiveRow findActiveIndex(
            @Param("tenantId") Long tenantId,
            @Param("knowledgeBaseId") Long knowledgeBaseId
    );

    List<KbDocumentChunkRetrievalRow> searchVectorCandidates(
            @Param("tenantId") Long tenantId,
            @Param("knowledgeBaseIds") List<Long> knowledgeBaseIds,
            @Param("embeddingModel") String embeddingModel,
            @Param("indexVersion") String indexVersion,
            @Param("embedding") String embedding,
            @Param("aclTokens") List<String> aclTokens,
            @Param("visibleAt") Instant visibleAt,
            @Param("scoreThreshold") double scoreThreshold,
            @Param("topK") int topK
    );

    List<KbDocumentChunkRetrievalRow> searchKeywordCandidates(
            @Param("tenantId") Long tenantId,
            @Param("knowledgeBaseIds") List<Long> knowledgeBaseIds,
            @Param("embeddingModel") String embeddingModel,
            @Param("indexVersion") String indexVersion,
            @Param("query") String query,
            @Param("aclTokens") List<String> aclTokens,
            @Param("visibleAt") Instant visibleAt,
            @Param("topK") int topK
    );

    int updateChunkEmbedding(
            @Param("tenantId") Long tenantId,
            @Param("chunkId") Long chunkId,
            @Param("embeddingModel") String embeddingModel,
            @Param("indexVersion") String indexVersion,
            @Param("embedding") String embedding,
            @Param("updatedAt") Instant updatedAt,
            @Param("updatedBy") Long updatedBy
    );

    int deleteObsoleteEmbeddings(
            @Param("updatedBefore") Instant updatedBefore,
            @Param("batchSize") int batchSize
    );

    String activateIfComplete(
            @Param("tenantId") Long tenantId,
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("embeddingModel") String embeddingModel,
            @Param("indexVersion") String indexVersion,
            @Param("activatedAt") Instant activatedAt,
            @Param("activatedBy") Long activatedBy
    );
}

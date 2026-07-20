package com.aiplatform.knowledge.infrastructure.persistence.mapper;

import com.aiplatform.knowledge.infrastructure.persistence.projection.KnowledgeIndexRebuildRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeIndexRebuildMapper {
    Long lockKnowledgeBase(@Param("tenantId") long tenantId, @Param("knowledgeBaseId") long knowledgeBaseId);

    Long nextRebuildId();

    Long nextGeneration(@Param("tenantId") long tenantId, @Param("knowledgeBaseId") long knowledgeBaseId);

    KnowledgeIndexRebuildRow findActive(@Param("tenantId") long tenantId, @Param("knowledgeBaseId") long knowledgeBaseId);

    KnowledgeIndexRebuildRow findById(@Param("tenantId") long tenantId, @Param("rebuildId") long rebuildId);

    int failActive(
            @Param("tenantId") long tenantId,
            @Param("knowledgeBaseId") long knowledgeBaseId,
            @Param("rebuildId") long rebuildId,
            @Param("failureCode") String failureCode,
            @Param("failureReason") String failureReason,
            @Param("now") Instant now,
            @Param("operatorId") long operatorId
    );

    int insertPlan(@Param("plan") KnowledgeIndexRebuildRow plan);

    int insertMember(
            @Param("tenantId") long tenantId,
            @Param("rebuildId") long rebuildId,
            @Param("knowledgeBaseId") long knowledgeBaseId,
            @Param("documentId") long documentId,
            @Param("documentUpdatedAt") Instant documentUpdatedAt,
            @Param("expectedChunkCount") int expectedChunkCount
    );

    int pointDesired(
            @Param("tenantId") long tenantId,
            @Param("knowledgeBaseId") long knowledgeBaseId,
            @Param("rebuildId") long rebuildId,
            @Param("generation") long generation,
            @Param("nextGenerationRequired") boolean nextGenerationRequired,
            @Param("operatorId") long operatorId,
            @Param("now") Instant now
    );

    int markNextGenerationRequired(
            @Param("tenantId") long tenantId,
            @Param("knowledgeBaseId") long knowledgeBaseId,
            @Param("operatorId") long operatorId,
            @Param("now") Instant now
    );

    Long lockLatestActive(
            @Param("tenantId") long tenantId,
            @Param("knowledgeBaseId") long knowledgeBaseId,
            @Param("rebuildId") long rebuildId,
            @Param("generation") long generation
    );

    List<Long> lockFrozenMembers(
            @Param("tenantId") long tenantId,
            @Param("rebuildId") long rebuildId,
            @Param("knowledgeBaseId") long knowledgeBaseId
    );

    Long lockCurrentMember(
            @Param("tenantId") long tenantId,
            @Param("rebuildId") long rebuildId,
            @Param("knowledgeBaseId") long knowledgeBaseId,
            @Param("documentId") long documentId,
            @Param("documentUpdatedAt") Instant documentUpdatedAt
    );

    Long activateLatestIfComplete(
            @Param("tenantId") long tenantId,
            @Param("knowledgeBaseId") long knowledgeBaseId,
            @Param("rebuildId") long rebuildId,
            @Param("generation") long generation,
            @Param("now") Instant now,
            @Param("operatorId") long operatorId
    );
}

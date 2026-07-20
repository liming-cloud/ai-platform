package com.aiplatform.knowledge.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class KnowledgeIndexRebuildMapperXmlContractTest {
    @Test
    void activationRequiresLatestActivePlanAndCompleteFrozenMembershipInOneStatement() throws IOException {
        String xml = mapperXml();

        assertThat(xml)
                .containsPattern("(?s)<select id=\"activateLatestIfComplete\".*FOR UPDATE OF plan, desired.*</select>")
                .contains("desired_rebuild_id = plan.id")
                .contains("desired_generation = plan.generation")
                .contains("plan.status = 'ACTIVE'")
                .contains("kb_knowledge_index_rebuild_member")
                .contains("member.document_updated_at = document.updated_at")
                .contains("member.expected_chunk_count")
                .contains("kb_document_chunk_embedding")
                .contains("candidate.embedding_model = plan.target_embedding_model")
                .contains("candidate.index_version = plan.index_version")
                .contains("INSERT INTO kb_knowledge_index_active")
                .contains("UPDATE kb_knowledge_index_rebuild")
                .contains("SET status = 'COMPLETED'")
                .contains("RETURNING rebuild.id");
    }

    @Test
    void planCreationLocksKnowledgeBaseAndUsesServerGenerationSequence() throws IOException {
        String xml = mapperXml();

        assertThat(xml)
                .containsPattern("(?s)<select id=\"lockKnowledgeBase\".*kb_knowledge_base.*FOR UPDATE.*</select>")
                .contains("nextGeneration")
                .contains("desired_generation + 1")
                .contains("insertPlan")
                .contains("insertMember")
                .contains("pointDesired");
    }

    @Test
    void finalizerCanLockOnlyAnUnchangedFrozenMember() throws IOException {
        assertThat(mapperXml())
                .containsPattern("(?s)<select id=\"lockCurrentMember\".*document_updated_at.*expected_chunk_count.*FOR UPDATE OF member, document.*</select>");
    }

    @Test
    void activationFinalizerLocksTheWholeFrozenCohortInStableOrder() throws IOException {
        assertThat(mapperXml())
                .containsPattern("(?s)<select id=\"lockFrozenMembers\".*ORDER BY member.document_id.*FOR UPDATE OF member, document.*</select>");
    }

    private static String mapperXml() throws IOException {
        try (var input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("mapper/knowledge/KnowledgeIndexRebuildMapper.xml")) {
            assertThat(input).as("KnowledgeIndexRebuildMapper.xml").isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

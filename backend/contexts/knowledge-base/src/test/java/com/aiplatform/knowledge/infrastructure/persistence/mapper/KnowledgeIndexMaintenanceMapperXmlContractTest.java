package com.aiplatform.knowledge.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class KnowledgeIndexMaintenanceMapperXmlContractTest {
    @Test
    void cleanupExcludesCurrentIndexAndEveryActiveFrozenCohortWithinTheSameTenant() throws IOException {
        String xml = mapperXml();

        assertThat(xml)
                .containsPattern("(?s)<delete id=\"deleteObsoleteEmbeddings\".*FOR UPDATE OF candidate SKIP LOCKED.*DELETE FROM kb_document_chunk_embedding.*</delete>")
                .contains("active.tenant_id = candidate.tenant_id")
                .contains("active.knowledge_base_id = candidate.knowledge_base_id")
                .contains("active.embedding_model = candidate.embedding_model")
                .contains("active.active_index_version = candidate.index_version")
                .contains("rebuild.tenant_id = candidate.tenant_id")
                .contains("rebuild.knowledge_base_id = candidate.knowledge_base_id")
                .contains("rebuild.target_embedding_model = candidate.embedding_model")
                .contains("rebuild.index_version = candidate.index_version")
                .contains("rebuild.status = 'ACTIVE'");
    }

    private static String mapperXml() throws IOException {
        try (var input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("mapper/knowledge/KbDocumentChunkMapper.xml")) {
            assertThat(input).as("KbDocumentChunkMapper.xml").isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

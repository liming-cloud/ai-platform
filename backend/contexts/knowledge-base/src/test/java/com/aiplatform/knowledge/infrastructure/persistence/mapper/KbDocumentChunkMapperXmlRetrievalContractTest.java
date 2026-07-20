package com.aiplatform.knowledge.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class KbDocumentChunkMapperXmlRetrievalContractTest {
    @Test
    void keywordRetrievalConstrainedByEmbeddingModelAndIndexedChunks() throws IOException {
        String xml = mapperXml("mapper/knowledge/KbDocumentChunkMapper.xml");

        assertThat(xml)
                .containsPattern("(?s)<select id=\"searchKeywordCandidates\".*JOIN kb_document_chunk_embedding.*index_version = #\\{indexVersion}.*</select>")
                .containsPattern("(?s)<select id=\"searchVectorCandidates\".*JOIN kb_document_chunk_embedding.*embedding_model = #\\{embeddingModel}.*</select>");
    }

    @Test
    void retrievalAppliesPrincipalAclBeforeRankingAndLimit() throws IOException {
        String xml = mapperXml("mapper/knowledge/KbDocumentChunkMapper.xml");

        assertThat(xml)
                .contains("<sql id=\"retrievalAccessPredicates\">")
                .contains("string_to_array(chunk.acl_tokens")
                .contains("#{visibleAt}")
                .containsPattern("(?s)<select id=\"searchVectorCandidates\".*<include refid=\"retrievalAccessPredicates\"/>.*LIMIT #\\{topK}.*</select>")
                .containsPattern("(?s)<select id=\"searchKeywordCandidates\".*<include refid=\"retrievalAccessPredicates\"/>.*LIMIT #\\{topK}.*</select>");
    }

    @Test
    void indexWritesAreVersionedAndActivationRequiresCompleteCoverage() throws IOException {
        String xml = mapperXml("mapper/knowledge/KbDocumentChunkMapper.xml");

        assertThat(xml)
                .containsPattern("(?s)<insert id=\"updateChunkEmbedding\".*INSERT INTO kb_document_chunk_embedding.*ON CONFLICT.*index_version.*DO UPDATE.*</insert>")
                .containsPattern("(?s)<select id=\"activateIfComplete\".*NOT EXISTS.*kb_document_chunk_embedding.*INSERT INTO kb_knowledge_index_active.*ON CONFLICT.*</select>");
    }

    private static String mapperXml(String resourceName) throws IOException {
        try (var input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            assertThat(input).as(resourceName).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

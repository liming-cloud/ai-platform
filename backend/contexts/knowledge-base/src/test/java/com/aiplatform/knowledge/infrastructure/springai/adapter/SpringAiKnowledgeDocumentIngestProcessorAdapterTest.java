package com.aiplatform.knowledge.infrastructure.springai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.springai.document.SpringAiDocumentEtlGateway;
import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadDescriptor;
import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadRequest;
import com.aiplatform.knowledge.domain.DocumentSourceContent;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.shared.kernel.TenantId;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.util.MimeTypeUtils;

class SpringAiKnowledgeDocumentIngestProcessorAdapterTest {
    @Test
    void adaptsSpringAiReaderAndSplitterToKnowledgeDocumentProcessorPort() {
        SpringAiKnowledgeDocumentIngestProcessorAdapter adapter = new SpringAiKnowledgeDocumentIngestProcessorAdapter();
        KnowledgeDocument document = document();

        var processed = adapter.process(
                document,
                new DocumentSourceContent(document.sourceUri(), "制度内容\n".getBytes(StandardCharsets.UTF_8))
        );

        assertThat(adapter.supports(document)).isTrue();
        assertThat(processed.processorName()).isEqualTo("spring-ai");
        assertThat(processed.chunkStrategyVersion()).isEqualTo("token:v1");
        assertThat(processed.chunks()).isNotEmpty();
        assertThat(processed.chunks().getFirst().content()).contains("制度内容");
    }

    @Test
    void rejectsSpringAiDocumentWithoutTextInsteadOfSkippingIt() {
        SpringAiDocumentEtlGateway gateway = mock(SpringAiDocumentEtlGateway.class);
        when(gateway.read(any(SpringAiDocumentReadRequest.class))).thenReturn(List.of(new Document(
                new Media(MimeTypeUtils.APPLICATION_OCTET_STREAM, URI.create("s3://aip-knowledge/doc.bin")),
                Map.of()
        )));
        SpringAiKnowledgeDocumentIngestProcessorAdapter adapter = new SpringAiKnowledgeDocumentIngestProcessorAdapter(gateway);
        KnowledgeDocument document = document();

        assertThatThrownBy(() -> adapter.process(
                document,
                new DocumentSourceContent(document.sourceUri(), "制度内容\n".getBytes(StandardCharsets.UTF_8))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Spring AI 文档读取结果缺失文本内容");
    }

    @Test
    void supportsCarriesDocumentBusinessMetadataInsteadOfEmptyFallback() {
        SpringAiDocumentEtlGateway gateway = mock(SpringAiDocumentEtlGateway.class);
        when(gateway.supportsRead(any(SpringAiDocumentReadDescriptor.class))).thenReturn(true);
        SpringAiKnowledgeDocumentIngestProcessorAdapter adapter = new SpringAiKnowledgeDocumentIngestProcessorAdapter(gateway);
        KnowledgeDocument document = document();

        assertThat(adapter.supports(document)).isTrue();

        ArgumentCaptor<SpringAiDocumentReadDescriptor> requestCaptor =
                ArgumentCaptor.forClass(SpringAiDocumentReadDescriptor.class);
        verify(gateway).supportsRead(requestCaptor.capture());
        assertThat(requestCaptor.getValue().metadata())
                .containsEntry("tenantId", "2001")
                .containsEntry("knowledgeBaseId", "7001")
                .containsEntry("documentId", "8001")
                .containsEntry("sourceUri", "s3://aip-knowledge/doc.txt");
    }

    private static KnowledgeDocument document() {
        return new KnowledgeDocument(
                TenantId.of("2001"),
                "8001",
                "7001",
                "员工手册.txt",
                "upload",
                "s3://aip-knowledge/doc.txt",
                "d".repeat(64),
                "text/plain",
                13L,
                1,
                KnowledgeDocumentStatus.UPLOADED,
                Instant.parse("2026-05-25T09:00:00Z"),
                Instant.parse("2026-05-25T09:00:00Z"),
                "1001"
        );
    }
}

package com.aiplatform.infra.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.springai.document.SpringAiDocumentEtlGateway;
import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadDescriptor;
import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadProbe;
import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadRequest;
import com.aiplatform.infra.springai.document.model.SpringAiDocumentSplitRequest;
import com.aiplatform.infra.springai.document.reader.registry.SpringAiDocumentReaderRegistry;
import com.aiplatform.infra.springai.document.reader.strategy.SpringAiDocumentReaderStrategy;
import com.aiplatform.infra.springai.document.splitter.registry.SpringAiDocumentSplitterRegistry;
import com.aiplatform.infra.springai.document.splitter.strategy.SpringAiDocumentSplitterStrategy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

class SpringAiDocumentEtlGatewayTest {
    @Test
    void readsTextAndCarriesBusinessMetadataIntoSpringAiDocuments() {
        CapturingVectorStore vectorStore = new CapturingVectorStore();
        SpringAiDocumentEtlGateway gateway = new SpringAiDocumentEtlGateway(vectorStore);

        List<Document> documents = gateway.readText(
                "employee-handbook.txt",
                "制度内容".getBytes(StandardCharsets.UTF_8),
                Map.of("tenantId", "2001", "knowledgeBaseId", "7001", "documentId", "8001")
        );

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getText()).isEqualTo("制度内容");
        assertThat(documents.getFirst().getMetadata())
                .containsEntry("tenantId", "2001")
                .containsEntry("knowledgeBaseId", "7001")
                .containsEntry("documentId", "8001");
    }

    @Test
    void splitsAndWritesDocumentsThroughSpringAiVectorStore() {
        CapturingVectorStore vectorStore = new CapturingVectorStore();
        SpringAiDocumentEtlGateway gateway = new SpringAiDocumentEtlGateway(vectorStore);
        List<Document> documents = gateway.readText(
                "employee-handbook.txt",
                "第一条 制度内容\n第二条 使用规范".getBytes(StandardCharsets.UTF_8),
                Map.of("tenantId", "2001", "knowledgeBaseId", "7001", "documentId", "8001")
        );

        List<Document> chunks = gateway.split(documents);
        gateway.write(chunks);

        assertThat(chunks).isNotEmpty();
        assertThat(vectorStore.added).hasSize(chunks.size());
        assertThat(vectorStore.added.getFirst().getMetadata())
                .containsEntry("tenantId", "2001")
                .containsEntry("knowledgeBaseId", "7001")
                .containsEntry("documentId", "8001");
    }

    @Test
    void selectsRegisteredReaderAndSplitterStrategies() {
        CapturingVectorStore vectorStore = new CapturingVectorStore();
        CapturingReaderStrategy readerStrategy = new CapturingReaderStrategy();
        CapturingSplitterStrategy splitterStrategy = new CapturingSplitterStrategy();
        SpringAiDocumentEtlGateway gateway = new SpringAiDocumentEtlGateway(
                vectorStore,
                new SpringAiDocumentReaderRegistry(List.of(readerStrategy)),
                new SpringAiDocumentSplitterRegistry(List.of(splitterStrategy)),
                "heading"
        );

        List<Document> documents = gateway.read(new SpringAiDocumentReadRequest(
                "employee-handbook.md",
                "text/markdown",
                "# 总则".getBytes(StandardCharsets.UTF_8),
                Map.of("tenantId", "2001")
        ));
        List<Document> chunks = gateway.split("heading", documents);
        gateway.write(chunks);

        assertThat(readerStrategy.requests).hasSize(1);
        assertThat(readerStrategy.requests.getFirst().sourceName()).isEqualTo("employee-handbook.md");
        assertThat(readerStrategy.requests.getFirst().contentType()).isEqualTo("text/markdown");
        assertThat(splitterStrategy.requests).hasSize(1);
        assertThat(splitterStrategy.requests.getFirst().splitterName()).isEqualTo("heading");
        assertThat(chunks).extracting(Document::getText).containsExactly("custom-chunk:custom-read:# 总则");
        assertThat(vectorStore.added).hasSize(1);
        assertThat(vectorStore.added.getFirst().getMetadata()).containsEntry("tenantId", "2001");
    }

    @Test
    void normalizesDefaultSplitterNameBeforeChunkStrategyVersion() {
        CapturingSplitterStrategy splitterStrategy = new CapturingSplitterStrategy();
        SpringAiDocumentEtlGateway gateway = new SpringAiDocumentEtlGateway(
                new CapturingVectorStore(),
                new SpringAiDocumentReaderRegistry(List.of(new CapturingReaderStrategy())),
                new SpringAiDocumentSplitterRegistry(List.of(splitterStrategy)),
                " heading "
        );
        List<Document> documents = List.of(new Document("制度内容", Map.of("tenantId", "2001")));

        gateway.split(documents);

        assertThat(gateway.defaultChunkStrategyVersion()).isEqualTo("heading:v1");
        assertThat(splitterStrategy.requests).hasSize(1);
        assertThat(splitterStrategy.requests.getFirst().splitterName()).isEqualTo("heading");
    }

    @Test
    void rejectsMissingBusinessMetadataInsteadOfEmptyFallback() {
        byte[] content = "制度内容".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new SpringAiDocumentReadRequest(
                "employee-handbook.txt",
                "text/plain",
                content,
                null
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("文档业务元数据不能为空");
        assertThatThrownBy(() -> new SpringAiDocumentReadRequest(
                "employee-handbook.txt",
                "text/plain",
                content,
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档业务元数据不能为空");
    }

    @Test
    void normalizesDocumentReadAndSplitRequestFactsBeforeStrategySelection() {
        SpringAiDocumentReadRequest readRequest = new SpringAiDocumentReadRequest(
                " employee-handbook.md ",
                " text/markdown ",
                "# 总则".getBytes(StandardCharsets.UTF_8),
                Map.of(" tenantId ", " 2001 ")
        );
        SpringAiDocumentSplitRequest splitRequest = new SpringAiDocumentSplitRequest(
                " heading ",
                List.of(new Document("制度内容", Map.of("tenantId", "2001")))
        );

        assertThat(readRequest.sourceName()).isEqualTo("employee-handbook.md");
        assertThat(readRequest.contentType()).isEqualTo("text/markdown");
        assertThat(readRequest.metadata()).containsEntry("tenantId", "2001");
        assertThat(splitRequest.splitterName()).isEqualTo("heading");
    }

    @Test
    void normalizesDocumentReadProbeWithoutRequiringDocumentContent() {
        SpringAiDocumentReadProbe probe = new SpringAiDocumentReadProbe(
                " employee-handbook.md ",
                " text/markdown ",
                Map.of(" tenantId ", " 2001 ")
        );

        assertThat(probe.sourceName()).isEqualTo("employee-handbook.md");
        assertThat(probe.contentType()).isEqualTo("text/markdown");
        assertThat(probe.metadata()).containsEntry("tenantId", "2001");
    }

    @Test
    void rejectsEmptyDocumentContentAndSplitDocumentsInsteadOfNoopFallback() {
        assertThatThrownBy(() -> new SpringAiDocumentReadRequest(
                "employee-handbook.txt",
                "text/plain",
                new byte[0],
                Map.of("tenantId", "2001")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档内容不能为空");

        assertThatThrownBy(() -> new SpringAiDocumentSplitRequest(
                "token",
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("待切分文档不能为空");
    }
}

final class CapturingReaderStrategy implements SpringAiDocumentReaderStrategy {
    final List<SpringAiDocumentReadRequest> requests = new ArrayList<>();

    @Override
    public String name() {
        return "markdown-reader";
    }

    @Override
    public boolean supports(SpringAiDocumentReadDescriptor request) {
        return "text/markdown".equals(request.contentType());
    }

    @Override
    public List<Document> read(SpringAiDocumentReadRequest request) {
        requests.add(request);
        return List.of(new Document(
                "custom-read:" + new String(request.content(), StandardCharsets.UTF_8),
                request.metadata()
        ));
    }
}

final class CapturingSplitterStrategy implements SpringAiDocumentSplitterStrategy {
    final List<SpringAiDocumentSplitRequest> requests = new ArrayList<>();

    @Override
    public String name() {
        return "heading";
    }

    @Override
    public boolean supports(SpringAiDocumentSplitRequest request) {
        return "heading".equals(request.splitterName());
    }

    @Override
    public List<Document> split(SpringAiDocumentSplitRequest request) {
        requests.add(request);
        return request.documents().stream()
                .map(document -> new Document("custom-chunk:" + document.getText(), document.getMetadata()))
                .toList();
    }
}

final class CapturingVectorStore implements VectorStore {
    final List<Document> added = new ArrayList<>();

    @Override
    public void add(List<Document> documents) {
        added.addAll(documents);
    }

    @Override
    public void delete(List<String> ids) {
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        return List.of();
    }
}

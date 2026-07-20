package com.aiplatform.infra.springai.document;

import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadDescriptor;
import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadRequest;
import com.aiplatform.infra.springai.document.model.SpringAiDocumentSplitRequest;
import com.aiplatform.infra.springai.document.reader.registry.SpringAiDocumentReaderRegistry;
import com.aiplatform.infra.springai.document.reader.strategy.SpringAiTextDocumentReaderStrategy;
import com.aiplatform.infra.springai.document.splitter.registry.SpringAiDocumentSplitterRegistry;
import com.aiplatform.infra.springai.document.splitter.strategy.SpringAiTokenTextSplitterStrategy;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Spring AI 文档 ETL 网关，统一编排读取、切分和向量存储写入，具体适配器通过策略注册表扩展。
 */
public class SpringAiDocumentEtlGateway {
    private final VectorStore vectorStore;
    private final SpringAiDocumentReaderRegistry readerRegistry;
    private final SpringAiDocumentSplitterRegistry splitterRegistry;
    private final String defaultSplitterName;

    public SpringAiDocumentEtlGateway() {
        this(
                null,
                new SpringAiDocumentReaderRegistry(List.of(new SpringAiTextDocumentReaderStrategy())),
                new SpringAiDocumentSplitterRegistry(List.of(new SpringAiTokenTextSplitterStrategy())),
                SpringAiTokenTextSplitterStrategy.NAME
        );
    }

    public SpringAiDocumentEtlGateway(VectorStore vectorStore) {
        this(
                vectorStore,
                new SpringAiDocumentReaderRegistry(List.of(new SpringAiTextDocumentReaderStrategy())),
                new SpringAiDocumentSplitterRegistry(List.of(new SpringAiTokenTextSplitterStrategy())),
                SpringAiTokenTextSplitterStrategy.NAME
        );
    }

    public SpringAiDocumentEtlGateway(
            VectorStore vectorStore,
            SpringAiDocumentReaderRegistry readerRegistry,
            SpringAiDocumentSplitterRegistry splitterRegistry,
            String defaultSplitterName
    ) {
        this.vectorStore = vectorStore;
        this.readerRegistry = Objects.requireNonNull(readerRegistry, "文档读取策略注册表不能为空");
        this.splitterRegistry = Objects.requireNonNull(splitterRegistry, "文档切分策略注册表不能为空");
        this.defaultSplitterName = requireText(defaultSplitterName, "默认文档切分策略");
    }

    public List<Document> readText(String sourceName, byte[] content, Map<String, Object> metadata) {
        return read(new SpringAiDocumentReadRequest(sourceName, "text/plain", content, metadata));
    }

    public List<Document> read(SpringAiDocumentReadRequest request) {
        Objects.requireNonNull(request, "文档读取请求不能为空");
        return readerRegistry.select(request).read(request);
    }

    public boolean supportsRead(SpringAiDocumentReadDescriptor descriptor) {
        return readerRegistry.supports(descriptor);
    }

    public List<Document> split(List<Document> documents) {
        return split(defaultSplitterName, documents);
    }

    public List<Document> split(String splitterName, List<Document> documents) {
        SpringAiDocumentSplitRequest request = new SpringAiDocumentSplitRequest(splitterName, documents);
        return splitterRegistry.select(request).split(request);
    }

    public void write(List<Document> documents) {
        Objects.requireNonNull(documents, "待写入文档不能为空");
        if (vectorStore == null) {
            throw new IllegalStateException("向量存储未配置，不能写入 Spring AI 向量索引");
        }
        vectorStore.add(documents);
    }

    public String defaultChunkStrategyVersion() {
        return defaultSplitterName + ":v1";
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }
}

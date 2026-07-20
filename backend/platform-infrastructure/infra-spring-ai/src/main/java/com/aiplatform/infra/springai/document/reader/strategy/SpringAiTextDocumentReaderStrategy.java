package com.aiplatform.infra.springai.document.reader.strategy;

import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadDescriptor;
import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadRequest;
import com.aiplatform.infra.springai.document.reader.resource.NamedByteArrayResource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;

/**
 * 基于 Spring AI TextReader 的默认文本读取策略，保留业务元数据用于后续向量检索过滤。
 */
public class SpringAiTextDocumentReaderStrategy implements SpringAiDocumentReaderStrategy {
    @Override
    public String name() {
        return "text";
    }

    @Override
    public boolean supports(SpringAiDocumentReadDescriptor request) {
        String normalized = request.contentType().toLowerCase(Locale.ROOT);
        return normalized.startsWith("text/")
                || normalized.startsWith("application/json")
                || normalized.startsWith("application/xml")
                || normalized.startsWith("application/x-ndjson");
    }

    @Override
    public List<Document> read(SpringAiDocumentReadRequest request) {
        TextReader reader = new TextReader(new NamedByteArrayResource(request.content(), request.sourceName()));
        reader.setCharset(StandardCharsets.UTF_8);
        reader.getCustomMetadata().putAll(request.metadata());
        return reader.get();
    }
}

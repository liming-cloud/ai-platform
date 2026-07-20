package com.aiplatform.infra.springai.document.reader.strategy;

import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadDescriptor;
import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadRequest;
import java.util.List;
import org.springframework.ai.document.Document;

/**
 * Spring AI 文档读取扩展点，用于把不同来源或格式转换为 Spring AI Document。
 */
public interface SpringAiDocumentReaderStrategy {
    String name();

    boolean supports(SpringAiDocumentReadDescriptor request);

    List<Document> read(SpringAiDocumentReadRequest request);
}

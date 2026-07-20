package com.aiplatform.infra.springai.document.splitter.strategy;

import com.aiplatform.infra.springai.document.model.SpringAiDocumentSplitRequest;
import java.util.List;
import org.springframework.ai.document.Document;

/**
 * Spring AI 文档切分扩展点，用于适配 token、标题、段落等不同切分算法。
 */
public interface SpringAiDocumentSplitterStrategy {
    String name();

    boolean supports(SpringAiDocumentSplitRequest request);

    List<Document> split(SpringAiDocumentSplitRequest request);
}

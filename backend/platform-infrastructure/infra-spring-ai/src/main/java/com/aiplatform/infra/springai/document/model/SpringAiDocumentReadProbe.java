package com.aiplatform.infra.springai.document.model;

import java.util.Map;

/**
 * Spring AI 文档读取支持性探测请求，只携带策略选择和审计所需的来源事实。
 */
public record SpringAiDocumentReadProbe(
        String sourceName,
        String contentType,
        Map<String, Object> metadata
) implements SpringAiDocumentReadDescriptor {
    public SpringAiDocumentReadProbe {
        sourceName = SpringAiDocumentReadFacts.requireText(sourceName, "sourceName");
        contentType = SpringAiDocumentReadFacts.requireText(contentType, "contentType");
        metadata = SpringAiDocumentReadFacts.normalizeMetadata(metadata);
    }
}

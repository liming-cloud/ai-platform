package com.aiplatform.infra.springai.document.model;

import java.util.Map;

/**
 * Spring AI 文档读取请求，封装来源名称、内容类型、原始内容和业务元数据。
 */
public record SpringAiDocumentReadRequest(
        String sourceName,
        String contentType,
        byte[] content,
        Map<String, Object> metadata
) implements SpringAiDocumentReadDescriptor {
    public SpringAiDocumentReadRequest {
        sourceName = SpringAiDocumentReadFacts.requireText(sourceName, "sourceName");
        contentType = SpringAiDocumentReadFacts.requireText(contentType, "contentType");
        content = SpringAiDocumentReadFacts.copyContent(content);
        metadata = SpringAiDocumentReadFacts.normalizeMetadata(metadata);
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

}

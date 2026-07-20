package com.aiplatform.infra.springai.document.model;

import java.util.Map;

/**
 * Spring AI 文档读取能力选择所需的来源事实，不承载原始内容。
 */
public interface SpringAiDocumentReadDescriptor {
    String sourceName();

    String contentType();

    Map<String, Object> metadata();
}

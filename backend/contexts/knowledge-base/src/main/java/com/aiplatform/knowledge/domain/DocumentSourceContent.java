package com.aiplatform.knowledge.domain;

/**
 * 从对象存储读取到的知识文档原始内容。
 */
public record DocumentSourceContent(
        String sourceUri,
        byte[] content
) {
    public DocumentSourceContent {
        sourceUri = requireText(sourceUri, "文档来源地址");
        content = copyContent(content, "文档源内容");
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static byte[] copyContent(byte[] value, String fieldName) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.clone();
    }
}

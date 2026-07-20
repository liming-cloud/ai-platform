package com.aiplatform.knowledge.domain.port.ingest.model;

import java.util.Objects;

/**
 * 文档处理端口产出的领域切片草稿，由领域服务补齐租户、权限和索引元数据。
 */
public record KnowledgeDocumentChunkDraft(
        String content,
        int tokenCount,
        Integer pageNo,
        int positionStart,
        int positionEnd
) {
    public KnowledgeDocumentChunkDraft {
        content = requireContent(content, "切片内容");
        if (tokenCount < 0) {
            throw new IllegalArgumentException("切片 token 数不能为负数");
        }
        if (positionStart < 0 || positionEnd < positionStart) {
            throw new IllegalArgumentException("切片位置范围非法");
        }
    }

    private static String requireContent(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}

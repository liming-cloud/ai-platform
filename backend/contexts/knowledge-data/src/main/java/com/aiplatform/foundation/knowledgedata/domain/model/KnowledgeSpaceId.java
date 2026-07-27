package com.aiplatform.foundation.knowledgedata.domain.model;

/**
 * 知识空间的稳定标识。
 */
public record KnowledgeSpaceId(long value) {

    public KnowledgeSpaceId {
        if (value <= 0) {
            throw new IllegalArgumentException("知识空间标识必须是正整数");
        }
    }

    public static KnowledgeSpaceId parse(String value) {
        try {
            return new KnowledgeSpaceId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("知识空间标识必须是十进制正整数", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}

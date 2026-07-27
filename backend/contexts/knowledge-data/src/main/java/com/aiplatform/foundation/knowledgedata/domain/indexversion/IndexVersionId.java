package com.aiplatform.foundation.knowledgedata.domain.indexversion;

/**
 * 索引版本标识。
 */
public record IndexVersionId(long value) {

    public IndexVersionId {
        if (value <= 0) {
            throw new IllegalArgumentException("索引版本标识必须是正整数");
        }
    }

    public static IndexVersionId parse(String value) {
        try {
            return new IndexVersionId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("索引版本标识必须是十进制正整数", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}

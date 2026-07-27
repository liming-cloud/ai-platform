package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 数据源的稳定标识。
 */
public record DataSourceId(long value) implements ValueObject {

    public DataSourceId {
        if (value <= 0) {
            throw new IllegalArgumentException("数据源标识必须是正整数");
        }
    }

    public static DataSourceId parse(String value) {
        try {
            return new DataSourceId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("数据源标识必须是十进制正整数", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}

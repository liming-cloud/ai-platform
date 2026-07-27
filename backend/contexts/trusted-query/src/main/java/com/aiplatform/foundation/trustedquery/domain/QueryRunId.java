package com.aiplatform.foundation.trustedquery.domain;

/**
 * 一次可信查询运行的稳定标识。
 */
public record QueryRunId(long value) {

    public QueryRunId {
        if (value <= 0) {
            throw new IllegalArgumentException("查询运行标识必须是正整数");
        }
    }

    public static QueryRunId parse(String value) {
        try {
            return new QueryRunId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("查询运行标识格式不正确", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}

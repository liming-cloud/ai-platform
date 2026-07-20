package com.aiplatform.knowledge.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * 文档入库任务状态，约束解析、索引、完成和失败的生命周期流转。
 */
public enum DocumentIngestStatus {
    PENDING,
    RETRY_WAIT,
    PARSING,
    INDEXING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public static DocumentIngestStatus from(String value) {
        Objects.requireNonNull(value, "文档入库任务状态不能为空");
        return DocumentIngestStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}

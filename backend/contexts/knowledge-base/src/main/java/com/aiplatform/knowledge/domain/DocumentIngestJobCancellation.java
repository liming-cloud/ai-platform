package com.aiplatform.knowledge.domain;

import java.util.Objects;

/**
 * 持久化取消结果。changed=false 表示请求命中了相同的已取消终态。
 */
public record DocumentIngestJobCancellation(DocumentIngestJobSnapshot snapshot, boolean changed) {
    public DocumentIngestJobCancellation {
        Objects.requireNonNull(snapshot, "取消作业快照不能为空");
        if (snapshot.status() != DocumentIngestStatus.CANCELLED) {
            throw new IllegalArgumentException("取消结果必须携带 CANCELLED 作业快照");
        }
    }
}

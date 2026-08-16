package com.aiplatform.foundation.knowledgedata.application.datasource.dto;

/** 对象存储登记页可见的名称化授权范围；内部引用和修订只由客户端透传。 */
public record PlatformObjectRangeCandidate(
        String rangeReference,
        long selectionRevision,
        String displayName,
        int objectCount) {
}

package com.aiplatform.appstudio.domain.port.debug.model;

import com.aiplatform.appstudio.domain.port.AppStudioPortContracts;
import com.aiplatform.shared.kernel.ValueObject;

/**
 * App Studio 调试运行的检索证据快照，保存当次回答所见的 evidence 事实。
 */
public record AppStudioDebugRunEvidenceSnapshot(
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        double score,
        String retrievalSignal,
        String indexVersion
) implements ValueObject {
    public AppStudioDebugRunEvidenceSnapshot {
        knowledgeBaseId = AppStudioPortContracts.requireText(knowledgeBaseId, "知识库 ID");
        documentId = AppStudioPortContracts.requireText(documentId, "文档 ID");
        chunkId = AppStudioPortContracts.requireText(chunkId, "切片 ID");
        if (score < 0) {
            throw new IllegalArgumentException("证据分数不能小于 0");
        }
        retrievalSignal = AppStudioPortContracts.requireText(retrievalSignal, "检索信号");
        indexVersion = AppStudioPortContracts.requireText(indexVersion, "索引版本");
    }
}

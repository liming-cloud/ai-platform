package com.aiplatform.foundation.assistantdelivery.domain.assistant.model;

/**
 * 助手版本冻结的受控嵌入模型配置与目录能力快照。
 */
public record GovernedEmbeddingModelBinding(
        long configurationId,
        long configurationRevision,
        long catalogEntryId,
        long catalogEntryRevision,
        int dimension) {

    public GovernedEmbeddingModelBinding {
        if (configurationId <= 0
                || configurationRevision < 1
                || catalogEntryId <= 0
                || catalogEntryRevision < 1
                || dimension < 1) {
            throw new IllegalArgumentException("受控嵌入配置、目录修订和维度必须大于零");
        }
    }
}

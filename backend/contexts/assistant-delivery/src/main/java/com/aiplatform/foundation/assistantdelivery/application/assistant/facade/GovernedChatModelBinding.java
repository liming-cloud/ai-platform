package com.aiplatform.foundation.assistantdelivery.application.assistant.facade;

/**
 * 助手版本冻结的受控对话模型配置与目录能力快照。
 */
public record GovernedChatModelBinding(
        long configurationId,
        long configurationRevision,
        long catalogEntryId,
        long catalogEntryRevision,
        int outputLimit) {

    public GovernedChatModelBinding {
        if (configurationId <= 0
                || configurationRevision < 1
                || catalogEntryId <= 0
                || catalogEntryRevision < 1
                || outputLimit < 1) {
            throw new IllegalArgumentException("受控对话配置、目录修订和输出上限必须大于零");
        }
    }
}

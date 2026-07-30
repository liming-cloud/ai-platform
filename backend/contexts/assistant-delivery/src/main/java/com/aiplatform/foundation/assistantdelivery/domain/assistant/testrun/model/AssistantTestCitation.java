package com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;

/**
 * 草稿测试历史保留的最小引用标识；不复制证据正文、摘要或评分。
 */
public record AssistantTestCitation(
        int sequence,
        long dataSourceId,
        int contentVersion,
        String chunkId,
        int ordinal) implements ValueObject {

    public AssistantTestCitation {
        if (sequence < 1 || dataSourceId <= 0 || contentVersion < 1 || ordinal < 0) {
            throw new IllegalArgumentException("助手测试引用标识不正确");
        }
        Objects.requireNonNull(chunkId, "分块标识不能为空");
        if (chunkId.isBlank() || !chunkId.equals(chunkId.trim())) {
            throw new IllegalArgumentException("分块标识格式不正确");
        }
    }
}

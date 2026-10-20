package com.aiplatform.foundation.modelruntime.application.extension.facade;

/** 扩展契约使用的模型能力枚举。 */
public enum ModelCapability {
    CHAT,
    STREAMING_CHAT,
    EMBEDDING,
    STRUCTURED_OUTPUT,
    TOOL_CALLING,
    RERANK
}

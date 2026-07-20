package com.aiplatform.modelhub.domain;

/**
 * 模型类型枚举，用于区分聊天、推理、向量化和多模态等运行时能力。
 */
public enum ModelType {
    CHAT,
    REASONING,
    EMBEDDING,
    RERANK,
    IMAGE_GENERATION,
    IMAGE_UNDERSTANDING,
    SPEECH_TO_TEXT,
    TEXT_TO_SPEECH
}

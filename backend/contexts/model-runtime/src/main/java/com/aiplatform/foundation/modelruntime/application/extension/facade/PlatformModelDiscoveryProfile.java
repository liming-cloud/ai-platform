package com.aiplatform.foundation.modelruntime.application.extension.facade;

/** 扩展契约使用的权威模型发现协议。 */
public enum PlatformModelDiscoveryProfile {
    DETERMINISTIC_CATALOG,
    OPENAI_MODELS_API,
    VOLCENGINE_ARK_CONTROL_PLANE,
    SILICONFLOW_RERANK_MODELS_API,
    NONE
}

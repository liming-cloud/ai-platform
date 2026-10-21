package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;

/** 平台连接显式冻结的权威模型发现协议。 */
public enum PlatformModelDiscoveryProfile {
    DETERMINISTIC_CATALOG,
    OPENAI_MODELS_API,
    VOLCENGINE_ARK_CONTROL_PLANE,
    SILICONFLOW_RERANK_MODELS_API,
    NONE;

    /** 判断发现协议是否可用于给定供应商连接类型。 */
    public boolean supports(ProviderKind providerKind) {
        return switch (this) {
            case DETERMINISTIC_CATALOG -> providerKind == ProviderKind.DETERMINISTIC;
            case OPENAI_MODELS_API, VOLCENGINE_ARK_CONTROL_PLANE, SILICONFLOW_RERANK_MODELS_API ->
                    providerKind == ProviderKind.OPENAI_COMPATIBLE;
            case NONE -> true;
        };
    }

    /**
     * 为历史连接提供唯一兼容迁移值；新公共写入必须显式提交发现协议。
     */
    public static PlatformModelDiscoveryProfile legacyDefault(ProviderKind providerKind) {
        return switch (providerKind) {
            case DETERMINISTIC -> DETERMINISTIC_CATALOG;
            case OPENAI_COMPATIBLE -> OPENAI_MODELS_API;
            case OLLAMA -> NONE;
        };
    }
}

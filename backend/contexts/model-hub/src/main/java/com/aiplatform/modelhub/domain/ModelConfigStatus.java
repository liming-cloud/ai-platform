package com.aiplatform.modelhub.domain;

/**
 * 模型配置业务状态，控制模型是否允许被平台运行时选择。
 */
public enum ModelConfigStatus {
    PENDING_APPROVAL,
    ACTIVE,
    INACTIVE,
    REJECTED,
    DEPRECATED
}

package com.aiplatform.foundation.modelruntime.application.deployment.dto;

/** 处理当前模型部署可用性状态的唯一建议下一步。 */
public enum ModelDeploymentAvailabilityNextStep {
    VERIFY_CONNECTION,
    PROBE_MODEL,
    REPROBE_MODEL,
    PUBLISH,
    NONE
}

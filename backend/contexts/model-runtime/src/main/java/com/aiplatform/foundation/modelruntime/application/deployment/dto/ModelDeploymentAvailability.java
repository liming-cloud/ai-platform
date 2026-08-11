package com.aiplatform.foundation.modelruntime.application.deployment.dto;

/**
 * 模型部署在当前供应商连接修订与能力验证事实下的只读可用性投影。
 *
 * <p>它不改变部署发布状态，也不能替代运行时授权与实际调用失败处理。</p>
 */
public enum ModelDeploymentAvailability {
    HEALTHY,
    DEGRADED,
    UNAVAILABLE
}

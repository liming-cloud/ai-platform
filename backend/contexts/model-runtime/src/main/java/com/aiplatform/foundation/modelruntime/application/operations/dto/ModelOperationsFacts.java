package com.aiplatform.foundation.modelruntime.application.operations.dto;

import java.util.Map;

/** 模型供应链上下文拥有的脱敏状态计数。 */
public record ModelOperationsFacts(
        Map<String, Long> platformConnectionsByStatus,
        Map<String, Long> platformDeploymentsByStatus,
        Map<String, Long> tenantConnectionsByStatus,
        Map<String, Long> tenantDeploymentsByStatus,
        Map<String, Long> configurationsByStatus) {

    public ModelOperationsFacts {
        platformConnectionsByStatus = Map.copyOf(platformConnectionsByStatus);
        platformDeploymentsByStatus = Map.copyOf(platformDeploymentsByStatus);
        tenantConnectionsByStatus = Map.copyOf(tenantConnectionsByStatus);
        tenantDeploymentsByStatus = Map.copyOf(tenantDeploymentsByStatus);
        configurationsByStatus = Map.copyOf(configurationsByStatus);
    }
}

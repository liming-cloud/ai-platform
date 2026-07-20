package com.aiplatform.modelhub.domain;

/**
 * 模型配置变更风险等级，用于审批、审计和发布门禁统一判断。
 */
public enum ModelConfigRiskLevel {
    NORMAL,
    HIGH_RISK;

    public String auditValue() {
        return this == HIGH_RISK ? "HIGH" : "NORMAL";
    }
}

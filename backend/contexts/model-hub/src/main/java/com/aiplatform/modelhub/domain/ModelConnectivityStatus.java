package com.aiplatform.modelhub.domain;

/**
 * 模型连通性状态，记录最近一次模型供应商和模型配置探测结果。
 */
public enum ModelConnectivityStatus {
    NOT_TESTED,
    PASSED,
    FAILED
}

package com.aiplatform.modelhub.domain;

import java.util.Objects;

/**
 * 模型运行时连通性探测结果，统一表达成功、失败和可展示原因。
 */
public record ModelRuntimeConnectivity(
        boolean connected,
        ModelConnectivityStatus status,
        String message
) {
    public ModelRuntimeConnectivity {
        Objects.requireNonNull(status, "模型连通性状态不能为空");
        requireConnectivityState(connected, status);
        message = requireText(message, "模型连通性说明");
    }

    private static void requireConnectivityState(boolean connected, ModelConnectivityStatus status) {
        if (connected && status != ModelConnectivityStatus.PASSED) {
            throw new IllegalArgumentException("模型连通性通过结果必须使用 PASSED 状态");
        }
        if (!connected && status != ModelConnectivityStatus.FAILED) {
            throw new IllegalArgumentException("模型连通性失败结果必须使用 FAILED 状态");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

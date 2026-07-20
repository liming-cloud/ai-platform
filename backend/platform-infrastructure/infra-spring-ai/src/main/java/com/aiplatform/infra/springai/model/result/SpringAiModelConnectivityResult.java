package com.aiplatform.infra.springai.model.result;

import java.util.Objects;

/**
 * Spring AI 模型连通性结果，区分网络可达和失败原因。
 */
public record SpringAiModelConnectivityResult(
        boolean connected,
        String status,
        String message
) {
    private static final String PASSED_STATUS = "PASSED";
    private static final String FAILED_STATUS = "FAILED";

    public SpringAiModelConnectivityResult {
        status = requireText(status, "连通性状态");
        requireConnectivityState(connected, status);
        message = requireText(message, "连通性说明");
    }

    public static SpringAiModelConnectivityResult passed(String message) {
        return new SpringAiModelConnectivityResult(true, PASSED_STATUS, message);
    }

    public static SpringAiModelConnectivityResult failed(String message) {
        return new SpringAiModelConnectivityResult(false, FAILED_STATUS, message);
    }

    private static void requireConnectivityState(boolean connected, String status) {
        if (connected && !PASSED_STATUS.equals(status)) {
            throw new IllegalArgumentException("连通性通过结果必须使用 PASSED 状态");
        }
        if (!connected && !FAILED_STATUS.equals(status)) {
            throw new IllegalArgumentException("连通性失败结果必须使用 FAILED 状态");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

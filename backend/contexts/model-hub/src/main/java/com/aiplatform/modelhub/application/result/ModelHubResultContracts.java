package com.aiplatform.modelhub.application.result;

/**
 * 模型中心应用层结果契约，集中约束跨结果对象复用的状态和文本语义。
 */
final class ModelHubResultContracts {
    private static final String CONNECTIVITY_STATUS_NOT_TESTED = "NOT_TESTED";
    private static final String CONNECTIVITY_STATUS_PASSED = "PASSED";
    private static final String CONNECTIVITY_STATUS_FAILED = "FAILED";

    private ModelHubResultContracts() {
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    static String normalizeConnectivityMessage(String connectivityStatus, String message) {
        String normalizedStatus = requireConnectivityStatus(connectivityStatus);
        if (CONNECTIVITY_STATUS_NOT_TESTED.equals(normalizedStatus)) {
            if (message != null && !message.isBlank()) {
                throw new IllegalArgumentException("未测试模型配置不得携带连通性说明");
            }
            return "";
        }
        return requireText(message, "模型连通性说明");
    }

    static String requireConnectivityStatus(String connectivityStatus) {
        String normalizedStatus = requireText(connectivityStatus, "模型连通性状态");
        if (!CONNECTIVITY_STATUS_NOT_TESTED.equals(normalizedStatus)
                && !CONNECTIVITY_STATUS_PASSED.equals(normalizedStatus)
                && !CONNECTIVITY_STATUS_FAILED.equals(normalizedStatus)) {
            throw new IllegalArgumentException("模型连通性状态不合法");
        }
        return normalizedStatus;
    }

    static String normalizeApprovalRequestId(String status, String approvalRequestId) {
        String normalized = approvalRequestId == null || approvalRequestId.isBlank()
                ? null
                : approvalRequestId.trim();
        if ("PENDING_APPROVAL".equals(status) && normalized == null) {
            throw new IllegalArgumentException("待审批资源必须携带审批请求 ID");
        }
        return normalized;
    }

    static void requireConnectivityResultState(boolean connected, String connectivityStatus) {
        String normalizedStatus = requireConnectivityStatus(connectivityStatus);
        if (connected && !CONNECTIVITY_STATUS_PASSED.equals(normalizedStatus)) {
            throw new IllegalArgumentException("模型连通性通过结果必须使用 PASSED 状态");
        }
        if (!connected && !CONNECTIVITY_STATUS_FAILED.equals(normalizedStatus)) {
            throw new IllegalArgumentException("模型连通性失败结果必须使用 FAILED 状态");
        }
    }
}

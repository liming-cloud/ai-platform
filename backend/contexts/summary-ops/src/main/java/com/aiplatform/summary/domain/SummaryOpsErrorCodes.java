package com.aiplatform.summary.domain;

import com.aiplatform.shared.kernel.ErrorCode;

public final class SummaryOpsErrorCodes {
    public static final ErrorCode AUTHORIZATION_DENIED = ErrorCode.of(
            "AIP-SUMMARY-AUTHZ-403",
            "无权读取汇总指标"
    );

    private SummaryOpsErrorCodes() {
    }
}

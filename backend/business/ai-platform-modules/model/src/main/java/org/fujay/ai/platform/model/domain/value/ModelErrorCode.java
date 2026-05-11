package org.fujay.ai.platform.model.domain.value;

import org.fujay.ai.platform.shared.error.ErrorCode;

public enum ModelErrorCode implements ErrorCode {
    MODEL_CAPABILITY_MISMATCH("MODEL-400-CAPABILITY", "模型能力类型不匹配"),
    PROVIDER_NOT_FOUND("MODEL-404-PROVIDER", "模型供应商不存在"),
    MODEL_NOT_FOUND("MODEL-404-MODEL", "模型不存在"),
    PROVIDER_TEST_FAILED("MODEL-502-PROVIDER", "模型供应商连接测试失败");

    private final String code;
    private final String message;

    ModelErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}

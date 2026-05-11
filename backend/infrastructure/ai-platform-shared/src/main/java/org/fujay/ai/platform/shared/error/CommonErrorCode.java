package org.fujay.ai.platform.shared.error;

public enum CommonErrorCode implements ErrorCode {
    INTERNAL_ERROR("COMMON-500", "服务内部错误"),
    UNAUTHORIZED("COMMON-401", "请先登录"),
    BAD_REQUEST("COMMON-400", "请求参数不正确"),
    FORBIDDEN("COMMON-403", "没有权限访问该资源");

    private final String code;
    private final String message;

    CommonErrorCode(String code, String message) {
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

package com.aiplatform.foundation.modelruntime.application.extension.facade.verification;

/** 只保留受控类别；不能包含供应商原文、SDK 异常或认证材料。 */
public final class PlatformCapabilityCallException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /** 平台样本传输的稳定拒绝原因。 */
    public enum Reason { UNSUPPORTED, ENDPOINT_REJECTED, SECRET_UNAVAILABLE, AUTHENTICATION_FAILED, RATE_LIMITED,
        TIMEOUT, RESPONSE_INVALID, RETRY_BLOCKED, CANCELLED, CALL_FAILED }
    private final Reason reason;
    public PlatformCapabilityCallException(Reason reason) { super("平台样本调用未完成：" + reason.name()); this.reason = reason; }
    /** 不提供厂商原文或原始异常链。 */
    public Reason reason() { return reason; }
}

package com.aiplatform.foundation.shared.kernel.error;

import java.util.Map;

/**
 * 表示数据库、网络、框架或外部厂商能力失败后的安全转译结果。
 */
public class InfrastructureException extends FoundationException {

    private static final long serialVersionUID = 1L;

    public InfrastructureException(ErrorCode errorCode) {
        super(errorCode);
    }

    public InfrastructureException(
            ErrorCode errorCode,
            Map<String, ?> details,
            Throwable cause) {
        super(errorCode, details, cause);
    }
}

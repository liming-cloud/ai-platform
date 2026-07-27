package com.aiplatform.foundation.shared.kernel.error;

import java.util.Map;

/**
 * 表示用例编排、权限、幂等或配额规则拒绝当前请求。
 */
public class ApplicationException extends FoundationException {

    private static final long serialVersionUID = 1L;

    public ApplicationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ApplicationException(ErrorCode errorCode, Map<String, ?> details) {
        super(errorCode, details);
    }

    public ApplicationException(
            ErrorCode errorCode,
            Map<String, ?> details,
            Throwable cause) {
        super(errorCode, details, cause);
    }
}

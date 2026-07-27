package com.aiplatform.foundation.shared.kernel.error;

import java.util.Map;

/**
 * 表示领域规则拒绝当前状态或行为。
 */
public class DomainException extends FoundationException {

    private static final long serialVersionUID = 1L;

    public DomainException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DomainException(ErrorCode errorCode, Map<String, ?> details) {
        super(errorCode, details);
    }
}

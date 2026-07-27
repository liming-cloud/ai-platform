package com.aiplatform.foundation.knowledgedata.application.datasource.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import java.util.Map;

/**
 * 原始文本为空、超限或不是严格 UTF-8。
 */
public final class InvalidTextContentException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public InvalidTextContentException(String message) {
        super(KnowledgeDataErrorCode.INVALID_TEXT_CONTENT);
    }

    public InvalidTextContentException(String message, Throwable cause) {
        super(KnowledgeDataErrorCode.INVALID_TEXT_CONTENT, Map.of(), cause);
    }
}

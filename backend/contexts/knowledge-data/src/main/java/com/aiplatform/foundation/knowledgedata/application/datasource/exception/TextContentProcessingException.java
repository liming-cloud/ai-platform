package com.aiplatform.foundation.knowledgedata.application.datasource.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.InfrastructureException;
import java.util.Map;

/**
 * 文本内容暂存或读取发生基础设施故障。
 */
public final class TextContentProcessingException extends InfrastructureException {

    private static final long serialVersionUID = 1L;

    public TextContentProcessingException(Throwable cause) {
        super(KnowledgeDataErrorCode.TEXT_CONTENT_PROCESSING_FAILURE, Map.of(), cause);
    }
}

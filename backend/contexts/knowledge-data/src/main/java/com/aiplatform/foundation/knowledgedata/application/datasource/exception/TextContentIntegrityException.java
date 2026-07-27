package com.aiplatform.foundation.knowledgedata.application.datasource.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.InfrastructureException;

/**
 * 对象存储返回的内容元数据与受治理事实不一致。
 */
public final class TextContentIntegrityException extends InfrastructureException {

    private static final long serialVersionUID = 1L;

    public TextContentIntegrityException() {
        super(KnowledgeDataErrorCode.TEXT_CONTENT_INTEGRITY_FAILURE);
    }
}

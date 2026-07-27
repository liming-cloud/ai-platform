package com.aiplatform.foundation.knowledgedata.domain.indexversion.model;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.DomainException;

/**
 * 索引版本状态不允许执行请求动作。
 */
public final class IndexVersionStateException extends DomainException {

    private static final long serialVersionUID = 1L;

    public IndexVersionStateException(String message) {
        super(KnowledgeDataErrorCode.INDEX_VERSION_STATE_CONFLICT);
    }
}

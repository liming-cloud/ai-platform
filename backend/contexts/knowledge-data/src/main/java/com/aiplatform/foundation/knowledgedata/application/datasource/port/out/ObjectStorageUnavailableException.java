package com.aiplatform.foundation.knowledgedata.application.datasource.port.out;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.InfrastructureException;
import java.util.Map;

/**
 * 对象存储当前无法提供受治理操作。
 */
public final class ObjectStorageUnavailableException extends InfrastructureException {

    private static final long serialVersionUID = 1L;

    public ObjectStorageUnavailableException() {
        super(KnowledgeDataErrorCode.OBJECT_STORAGE_UNAVAILABLE);
    }

    public ObjectStorageUnavailableException(Throwable cause) {
        super(KnowledgeDataErrorCode.OBJECT_STORAGE_UNAVAILABLE, Map.of(), cause);
    }
}

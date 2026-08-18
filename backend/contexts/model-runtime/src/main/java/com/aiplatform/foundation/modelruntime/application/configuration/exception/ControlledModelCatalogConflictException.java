package com.aiplatform.foundation.modelruntime.application.configuration.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/** 相同厂商、地址、模型和类型的受控目录条目已经存在。 */
public final class ControlledModelCatalogConflictException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ControlledModelCatalogConflictException() {
        super(ModelRuntimeErrorCode.CATALOG_ENTRY_CONFLICT);
    }

    public ControlledModelCatalogConflictException(Throwable cause) {
        super(ModelRuntimeErrorCode.CATALOG_ENTRY_CONFLICT, java.util.Map.of(), cause);
    }
}

package com.aiplatform.foundation.modelruntime.application.provider.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
/**
 * 可信主体缺少模型目录操作权限。
 */
public final class ModelCatalogPermissionDeniedException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ModelCatalogPermissionDeniedException() {
        super(ModelRuntimeErrorCode.ACCESS_DENIED);
    }
}

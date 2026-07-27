package com.aiplatform.foundation.modelruntime.application.catalog.common;

/**
 * 可信主体缺少模型目录操作权限。
 */
public final class ModelCatalogPermissionDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelCatalogPermissionDeniedException() {
        super("没有执行该模型治理操作的权限");
    }
}

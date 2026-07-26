package com.aiplatform.foundation.modelruntime.api.catalog.model;

import java.util.List;
import java.util.Objects;

/**
 * 模型部署游标分页。
 */
public record ModelDeploymentPage(
        List<ModelDeploymentView> items,
        String nextCursor) {

    public ModelDeploymentPage {
        items = List.copyOf(Objects.requireNonNull(items, "模型部署列表不能为空"));
    }
}

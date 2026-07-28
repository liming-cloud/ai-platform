package com.aiplatform.foundation.modelruntime.domain.configuration.repository;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import java.util.List;
import java.util.Objects;

/**
 * 当前成员可见模型配置的领域分页事实。
 */
public record ModelConfigurationPage(
        List<ModelConfiguration> items,
        long pageNumber,
        long pageSize,
        long totalElements) {

    public ModelConfigurationPage {
        items = List.copyOf(Objects.requireNonNull(items, "模型配置列表不能为空"));
        if (pageNumber < 1 || pageSize < 1 || totalElements < items.size()) {
            throw new IllegalArgumentException("模型配置分页参数不正确");
        }
    }
}

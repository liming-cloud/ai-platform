package com.aiplatform.foundation.modelruntime.domain.configuration.repository;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import java.util.List;
import java.util.Objects;

/**
 * 已发布受控模型目录的分页读取结果，不包含任何配置凭据或所有权事实。
 */
public record ControlledModelCatalogPage(
        List<ControlledModelCatalogEntry> items,
        long pageNumber,
        long pageSize,
        long totalElements) {

    public ControlledModelCatalogPage {
        items = List.copyOf(Objects.requireNonNull(items, "受控模型目录分页条目不能为空"));
        if (pageNumber < 1 || pageSize < 1 || totalElements < 0) {
            throw new IllegalArgumentException("受控模型目录分页参数不正确");
        }
        if (items.size() > pageSize) {
            throw new IllegalArgumentException("受控模型目录分页条目超过页大小");
        }
    }
}

package com.aiplatform.foundation.modelruntime.domain.provider.repository;

import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import java.util.List;
import java.util.Objects;

/**
 * 供应商连接仓储分页切片。
 */
public record ProviderConnectionPage(
        List<ProviderConnection> items,
        long totalElements) {

    public ProviderConnectionPage {
        items = List.copyOf(Objects.requireNonNull(items, "供应商连接列表不能为空"));
        if (totalElements < 0L) {
            throw new IllegalArgumentException("供应商连接总数不能为负数");
        }
    }
}

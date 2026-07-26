package com.aiplatform.foundation.modelruntime.api.catalog.model;

import java.util.List;
import java.util.Objects;

/**
 * 供应商连接游标分页结果。
 */
public record ProviderConnectionPage(
        List<ProviderConnectionView> items,
        String nextCursor) {

    public ProviderConnectionPage {
        items = List.copyOf(Objects.requireNonNull(items, "供应商连接列表不能为空"));
    }
}

package com.aiplatform.foundation.modelruntime.application.port.out;

import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnection;
import java.util.List;
import java.util.Objects;

/**
 * 供应商连接仓储分页切片。
 */
public record ProviderConnectionSlice(
        List<ProviderConnection> items,
        String nextCursor) {

    public ProviderConnectionSlice {
        items = List.copyOf(Objects.requireNonNull(items, "供应商连接列表不能为空"));
    }
}

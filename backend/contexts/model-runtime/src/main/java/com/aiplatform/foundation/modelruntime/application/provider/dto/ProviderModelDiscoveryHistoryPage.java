package com.aiplatform.foundation.modelruntime.application.provider.dto;

import java.util.List;
import java.util.Objects;

/** 供应商连接模型发现历史的服务器分页读取结果。 */
public record ProviderModelDiscoveryHistoryPage(
        List<ProviderModelDiscoveryHistoryItem> items,
        long totalElements) {

    public ProviderModelDiscoveryHistoryPage {
        items = List.copyOf(Objects.requireNonNull(items, "模型发现历史不能为空"));
        if (totalElements < 0) {
            throw new IllegalArgumentException("模型发现历史总数不能为负数");
        }
    }
}

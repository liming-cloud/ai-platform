package com.aiplatform.foundation.modelruntime.domain.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import java.util.List;
import java.util.Objects;

/** 平台供应商连接的服务端分页切片。 */
public record PlatformProviderConnectionPage(
        List<PlatformProviderConnection> items,
        long totalElements) {

    public PlatformProviderConnectionPage {
        items = List.copyOf(Objects.requireNonNull(items, "平台供应商连接列表不能为空"));
        if (totalElements < 0) {
            throw new IllegalArgumentException("平台供应商连接总数不能为负数");
        }
    }
}

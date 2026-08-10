package com.aiplatform.foundation.modelruntime.application.provider.dto;

import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import java.util.List;
import java.util.Objects;

/** 当前有效发现候选的基础设施读取结果，不包含连接或供应商诊断信息。 */
public record ProviderModelDiscoveryCandidatePage(
        List<DiscoveredModel> items,
        long totalElements) {

    public ProviderModelDiscoveryCandidatePage {
        items = List.copyOf(Objects.requireNonNull(items, "候选模型不能为空"));
        if (totalElements < 0) {
            throw new IllegalArgumentException("候选模型总数不能为负数");
        }
    }
}

package com.aiplatform.foundation.modelruntime.domain.deployment.repository;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeployment;
import java.util.List;
import java.util.Objects;

/**
 * 模型部署持久化分页结果。
 */
public record ModelDeploymentPage(
        List<ModelDeployment> items,
        long totalElements) {

    public ModelDeploymentPage {
        items = List.copyOf(Objects.requireNonNull(items, "模型部署列表不能为空"));
        if (totalElements < 0L) {
            throw new IllegalArgumentException("模型部署总数不能为负数");
        }
    }
}

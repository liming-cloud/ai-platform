package com.aiplatform.foundation.modelruntime.application.port.out;

import com.aiplatform.foundation.modelruntime.domain.model.ModelDeployment;
import java.util.List;
import java.util.Objects;

/**
 * 模型部署持久化分页结果。
 */
public record ModelDeploymentSlice(
        List<ModelDeployment> items,
        String nextCursor) {

    public ModelDeploymentSlice {
        items = List.copyOf(Objects.requireNonNull(items, "模型部署列表不能为空"));
    }
}

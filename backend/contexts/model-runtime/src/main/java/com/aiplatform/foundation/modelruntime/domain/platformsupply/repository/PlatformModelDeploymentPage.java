package com.aiplatform.foundation.modelruntime.domain.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.List;
import java.util.Objects;

/** 平台模型部署的服务端分页读取结果。 */
public record PlatformModelDeploymentPage(List<PlatformModelDeployment> items, long totalElements)
        implements ValueObject {
    public PlatformModelDeploymentPage {
        items = List.copyOf(Objects.requireNonNull(items, "平台模型部署条目不能为空"));
        if (totalElements < items.size()) {
            throw new IllegalArgumentException("平台模型部署总数不能小于当前页条目数");
        }
    }
}

package com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate;
import java.util.Objects;

/** 当前完整发现根中的精确候选快照；名称与编码、管理员声明与厂商分类分别保留。 */
public record PlatformModelCandidateSnapshot(long discoveryId, PlatformDiscoveredModelCandidate candidate) {
    public PlatformModelCandidateSnapshot {
        if (discoveryId <= 0) throw new IllegalArgumentException("发现根标识不正确");
        Objects.requireNonNull(candidate, "候选不能为空");
    }
}

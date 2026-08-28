package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import java.time.Instant;

/** 原子确认当前平台连接修订中仍有效的待确认模型候选。 */
public interface PlatformProviderModelCandidateConfirmationStore {

    /**
     * 固化管理员选择的模型类型与最小对应能力，并记录确认主体。
     *
     * @throws IllegalArgumentException 候选不存在、已过期、已确认或连接修订不匹配。
     */
    PlatformDiscoveredModelCandidate confirmCurrentCandidate(
            PlatformProviderConnectionId connectionId,
            long connectionRevision,
            String modelCode,
            ModelType modelType,
            long confirmedBySubjectId,
            Instant confirmedAt,
            Instant now);
}

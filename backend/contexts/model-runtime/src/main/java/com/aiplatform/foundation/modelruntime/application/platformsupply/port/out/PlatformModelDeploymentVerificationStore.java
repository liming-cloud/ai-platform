package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.service.PlatformModelDeploymentVerificationResult;

/** 在同一事务中保存平台验证事实与可能发生的部署状态修订。 */
@FunctionalInterface
public interface PlatformModelDeploymentVerificationStore {
    void commit(PlatformModelDeploymentVerificationResult verification, long expectedDeploymentVersion);
}

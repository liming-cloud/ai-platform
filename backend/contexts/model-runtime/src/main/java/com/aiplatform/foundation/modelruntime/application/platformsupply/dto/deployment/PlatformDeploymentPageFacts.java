package com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentCounts;

/** S2 辅助读取的独立事实；缺失时前端必须只读，不能猜测默认值。 */
public record PlatformDeploymentPageFacts(boolean writesAvailable, PlatformModelDeploymentCounts counts) { }

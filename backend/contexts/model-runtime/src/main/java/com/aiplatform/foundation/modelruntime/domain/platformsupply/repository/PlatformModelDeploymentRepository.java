package com.aiplatform.foundation.modelruntime.domain.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import java.util.Optional;

/** 平台模型部署持久化端口，不接受租户范围参数。 */
public interface PlatformModelDeploymentRepository {
    /** 全目录统计由存储生成，不扫描客户端当前页。 */
    PlatformModelDeploymentCounts counts();
    /** 读取被部署明确引用的真实验证时间，不从有效期倒推。 */
    Optional<java.time.Instant> lastVerifiedAt(PlatformModelDeploymentId id);
    PlatformModelDeploymentCreationOutcome create(PlatformModelDeploymentCreation creation);
    Optional<PlatformModelDeployment> findById(PlatformModelDeploymentId id);
    PlatformModelDeploymentPage findPage(long pageNumber, long pageSize);
    boolean update(PlatformModelDeployment deployment, long expectedVersion);
}

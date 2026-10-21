package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.publication;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.publication.PlatformSupplyFacts;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** 原子发布目录／供应授权／审计，撤权及停用不改写或删除租户配置。 */
public interface PlatformModelSupplyPublicationStore {
    /** 复核完整验证与精确输入后原子发布；失败不能留下目录或部分授权。 */
    PlatformModelDeployment publish(PlatformRequestContext actor, PlatformModelDeployment expected, BooleanSupplier sessionActive);
    /** 按授权修订撤销一个已选租户，重复请求不得重新授权，审计与撤权同事务。 */
    void revoke(PlatformRequestContext actor, PlatformModelDeploymentId id, long tenantId,
            long expectedDeploymentVersion, long expectedGrantVersion, BooleanSupplier sessionActive);
    /** 安全停用不受 S2 写入暂停限制，仍须当前权限、会话与 CAS。 */
    PlatformModelDeployment disable(PlatformRequestContext actor, PlatformModelDeployment expected, BooleanSupplier sessionActive);
    /** 只返回该平台对象的独立发布、授权及依赖数量。 */
    Optional<PlatformSupplyFacts> facts(PlatformModelDeploymentId id);
}

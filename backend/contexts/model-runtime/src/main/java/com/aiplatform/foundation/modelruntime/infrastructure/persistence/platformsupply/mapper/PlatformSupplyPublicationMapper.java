package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformSupplyFactsRow;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** 原子供应发布、撤权与独立审计，所有写入由短事务和部署根锁保护。 */
public interface PlatformSupplyPublicationMapper {
    /** 拒绝旧最小验证、伪造样本、错输入及没有末尾审计的成功。 */
    Long completeVerification(@Param("d") PlatformModelDeployment deployment, @Param("inputs") String inputs,
            @Param("target") String target, @Param("sampleVersion") String sampleVersion,
            @Param("samples") String samples, @Param("now") Instant now);
    /** 冻结唯一目录与发布操作者，不能由客户端指定目录或授权集合。 */
    int publish(@Param("id") long id, @Param("d") PlatformModelDeployment deployment,
            @Param("catalogId") long catalogId, @Param("actor") PlatformRequestContext actor,
            @Param("inputs") String inputs, @Param("now") Instant now);
    /** 整个计划集合一次写入，任一失败都必须回滚目录和发布。 */
    int grant(@Param("publicationId") long publicationId, @Param("tenants") String tenants, @Param("now") Instant now);
    /** 锁定精确授权版本；撤权不能创建新关系或改变其他租户。 */
    Long lockGrant(@Param("publicationId") long publicationId, @Param("tenantId") long tenantId);
    /** 只撤销仍活动的精确授权版本，冲突不重放为新授权。 */
    int revoke(@Param("publicationId") long publicationId, @Param("tenantId") long tenantId,
            @Param("version") long version, @Param("now") Instant now);
    /** 独立安全审计最后写入，失败回滚同一事务全部变更。 */
    int audit(@Param("id") long id, @Param("deploymentId") long deploymentId,
            @Param("publicationId") Long publicationId, @Param("tenantId") Long tenantId,
            @Param("actor") PlatformRequestContext actor, @Param("action") String action, @Param("now") Instant now);
    /** 计数来自正式关系与配置依赖，不把草稿选择解释为活动授权。 */
    PlatformSupplyFactsRow facts(@Param("deploymentId") long deploymentId);
}

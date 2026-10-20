package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.time.Instant;
import java.util.Optional;

/** 仅向平台部署编排提供当前连接修订的未过期发现候选。 */
public interface PlatformProviderModelDiscoveryLookup {
    /** 分页读取已发布连接当前发现候选，不创建确认或部署事实。 */
    PageResult<com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment.PlatformDeployableModel> findDeployableModels(PageQuery page, Instant now);

    /** 只在当前完整发现根选择精确候选；未知分类由草稿事务独立确认。 */
    Optional<com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment.PlatformModelCandidateSnapshot> findCurrentSnapshot(
            PlatformProviderConnectionId connectionId, long connectionRevision, String modelCode, ModelType modelType, Instant now);

    Optional<PlatformDiscoveredModelCandidate> findCurrentCandidate(
            PlatformProviderConnectionId connectionId,
            long connectionRevision,
            String modelCode,
            ModelType modelType,
            Instant now);

    /**
     * 分页读取指定平台连接修订下仍有效的发现候选。
     *
     * <p>返回值只含模型代码、类型与能力，不能承载端点、凭据引用或任何探测原文。</p>
     */
    PageResult<PlatformDiscoveredModelCandidate> findCurrentCandidates(
            PlatformProviderConnectionId connectionId,
            long connectionRevision,
            PageQuery page,
            Instant now, String q);
}

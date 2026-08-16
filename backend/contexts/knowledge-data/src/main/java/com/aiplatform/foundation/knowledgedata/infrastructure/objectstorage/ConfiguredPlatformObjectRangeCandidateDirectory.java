package com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage;

import com.aiplatform.foundation.knowledgedata.application.datasource.dto.PlatformObjectRangeCandidate;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectRangeCandidateDirectory;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.infrastructure.configuration.KnowledgeDataProperties;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;

/** 将本地受控配置投影为不暴露桶名、对象键和内部位置的名称选择器。 */
public final class ConfiguredPlatformObjectRangeCandidateDirectory
        implements PlatformObjectRangeCandidateDirectory {
    private final KnowledgeDataProperties properties;

    public ConfiguredPlatformObjectRangeCandidateDirectory(KnowledgeDataProperties properties) {
        this.properties = Objects.requireNonNull(properties, "知识数据配置不能为空");
    }

    @Override
    public PageResult<PlatformObjectRangeCandidate> page(
            TenantId tenantId, KnowledgeSpaceId knowledgeSpaceId, PageQuery page) {
        var candidates = properties.getObjectStorageRanges().stream()
                .filter(range -> range.getTenantId() == tenantId.value()
                        && range.getKnowledgeSpaceId() == knowledgeSpaceId.value())
                .map(range -> new PlatformObjectRangeCandidate(
                        range.getRangeId(), range.getSelectionRevision(),
                        "已授权对象范围 · " + range.getObjects().size() + " 个对象",
                        range.getObjects().size()))
                .toList();
        var from = Math.min(Math.toIntExact(page.offset()), candidates.size());
        var to = Math.min(from + Math.toIntExact(page.pageSize()), candidates.size());
        return PageResult.of(candidates.subList(from, to), page, candidates.size());
    }
}

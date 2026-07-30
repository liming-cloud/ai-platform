package com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage;

import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectRangeSelectionPort;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.ObjectStorageObjectCollection;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectRangeId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.infrastructure.configuration.KnowledgeDataProperties;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;

/** 从部署静态配置解析已授权的平台对象范围选择。 */
public final class ConfiguredPlatformObjectRangeSelectionAdapter implements PlatformObjectRangeSelectionPort {
    private final java.util.Map<Key, ObjectStorageObjectCollection> selections;

    public ConfiguredPlatformObjectRangeSelectionAdapter(KnowledgeDataProperties properties) {
        Objects.requireNonNull(properties, "知识数据配置不能为空");
        var indexed = new HashMap<Key, ObjectStorageObjectCollection>();
        for (var range : properties.getObjectStorageRanges()) {
            var rangeId = new PlatformObjectRangeId(range.getRangeId());
            var collection = new ObjectStorageObjectCollection(rangeId, range.getSelectionRevision(), range.getObjects().stream()
                    .map(object -> new PlatformObjectReference(object.getObjectId(), object.getContentDigest(), object.getByteSize(), object.getMediaType(), Instant.parse(object.getFreshAt())))
                    .toList());
            var key = new Key(new TenantId(range.getTenantId()), new KnowledgeSpaceId(range.getKnowledgeSpaceId()), rangeId, range.getSelectionRevision());
            if (indexed.putIfAbsent(key, collection) != null) throw new IllegalArgumentException("平台对象范围选择不能重复");
        }
        selections = java.util.Map.copyOf(indexed);
    }

    @Override public ObjectStorageObjectCollection resolve(TenantId tenantId, KnowledgeSpaceId knowledgeSpaceId, PlatformObjectRangeId rangeId, long selectionRevision) {
        var collection = selections.get(new Key(tenantId, knowledgeSpaceId, rangeId, selectionRevision));
        if (collection == null) throw new IllegalArgumentException("平台对象范围选择不存在或不可访问");
        return collection;
    }

    private record Key(TenantId tenantId, KnowledgeSpaceId knowledgeSpaceId, PlatformObjectRangeId rangeId, long selectionRevision) { }
}

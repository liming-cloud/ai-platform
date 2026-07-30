package com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectRangeId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.infrastructure.configuration.KnowledgeDataProperties;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfiguredPlatformObjectRangeSelectionAdapterTest {

    @Test
    void 只解析同租户知识空间的已配置范围选择() {
        var properties = new KnowledgeDataProperties();
        var range = new KnowledgeDataProperties.ObjectStorageRange();
        range.setTenantId(101L);
        range.setKnowledgeSpaceId(202L);
        range.setRangeId("rng_7c90d7a8716e4a79");
        range.setSelectionRevision(7);
        var object = new KnowledgeDataProperties.ObjectStorageRangeObject();
        object.setObjectId("obj_26d372bcbd4245b7");
        object.setContentDigest("a".repeat(64));
        object.setByteSize(128);
        object.setMediaType("text/plain; charset=utf-8");
        object.setFreshAt("2026-07-30T01:00:00Z");
        range.setObjects(List.of(object));
        properties.setObjectStorageRanges(List.of(range));
        var adapter = new ConfiguredPlatformObjectRangeSelectionAdapter(properties);

        var resolved = adapter.resolve(new TenantId(101L), new KnowledgeSpaceId(202L),
                new PlatformObjectRangeId("rng_7c90d7a8716e4a79"), 7);

        assertEquals(1, resolved.objects().size());
        assertThrows(IllegalArgumentException.class, () -> adapter.resolve(
                new TenantId(102L), new KnowledgeSpaceId(202L),
                new PlatformObjectRangeId("rng_7c90d7a8716e4a79"), 7));
    }
}

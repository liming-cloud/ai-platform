package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.ObjectStorageObjectCollection;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectRangeId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectStorageSourceRevisionTest {

    private static final TenantId TENANT = new TenantId(101L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(202L);
    private static final CreatorId CREATOR = new CreatorId(303L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-30T02:00:00Z");

    @Test
    void 对象存储修订冻结平台范围选择及对象集合() {
        var source = source(GovernedDataSourceType.OBJECT_STORAGE);
        var collection = collection();

        var revision = SourceRevision.objectStorage(source, collection, CREATOR, CREATED_AT);

        assertEquals(collection, revision.objectStorageObjects());
        assertEquals(collection.definitionDigest(), revision.definitionDigest());
        assertEquals(new PlatformObjectRangeId("rng_7c90d7a8716e4a79"), collection.rangeId());
        assertEquals(7, collection.selectionRevision());
    }

    @Test
    void 对象存储集合拒绝重复的平台对象标识() {
        var object = object("obj_26d372bcbd4245b7", "a".repeat(64));

        assertThrows(IllegalArgumentException.class, () -> new ObjectStorageObjectCollection(
                new PlatformObjectRangeId("rng_7c90d7a8716e4a79"),
                7,
                List.of(object, object)));
    }

    @Test
    void 对象集合只能绑定对象存储来源() {
        assertThrows(IllegalArgumentException.class, () -> SourceRevision.objectStorage(
                source(GovernedDataSourceType.FILE), collection(), CREATOR, CREATED_AT));
    }

    private static ObjectStorageObjectCollection collection() {
        return new ObjectStorageObjectCollection(
                new PlatformObjectRangeId("rng_7c90d7a8716e4a79"),
                7,
                List.of(
                        object("obj_26d372bcbd4245b7", "a".repeat(64)),
                        object("obj_6a6256645ef6487f", "b".repeat(64))));
    }

    private static PlatformObjectReference object(String id, String digest) {
        return new PlatformObjectReference(
                id,
                digest,
                128,
                "text/plain; charset=utf-8",
                Instant.parse("2026-07-30T01:00:00Z"));
    }

    private static GovernedDataSource source(GovernedDataSourceType type) {
        return GovernedDataSource.create(
                TENANT,
                SPACE,
                new DataSourceId(404L),
                type,
                "受治理对象存储",
                CREATOR,
                CREATED_AT);
    }
}

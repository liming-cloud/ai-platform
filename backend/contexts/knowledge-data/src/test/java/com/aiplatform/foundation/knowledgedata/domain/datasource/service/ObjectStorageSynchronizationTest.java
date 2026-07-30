package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.ObjectStorageObjectCollection;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectRangeId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectStorageSynchronizationTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE_ID = new DataSourceId(230000000000000001L);
    private static final CreatorId CREATOR = new CreatorId(4921825928971026434L);
    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");

    private final ObjectStorageSynchronization synchronization = new ObjectStorageSynchronization();

    @Test
    void 应以冻结对象集合完成同步并创建通过质量结论() {
        var ready = readySource();
        var syncing = synchronization.begin(ready, ready.version());

        var completion = synchronization.complete(
                syncing,
                revision(ready),
                new SyncArtifactId(240000000000000001L),
                normalizedContent(1),
                NOW);

        assertEquals(GovernedDataSourceStatus.READY, completion.completedSource().status());
        assertEquals(2, completion.artifact().recordCount());
        assertEquals(QualityStatus.PASS, completion.qualityReport().status());
        assertEquals("object-storage-text-v1", completion.qualityReport().ruleSetVersion());
    }

    @Test
    void 非对象存储来源或不属于当前修订的内容不得完成同步() {
        var file = GovernedDataSource.create(
                TENANT, SPACE, SOURCE_ID, GovernedDataSourceType.FILE, "文件", CREATOR, NOW);
        assertThrows(DataSourceStateException.class, () -> synchronization.begin(file, 0));

        var ready = readySource();
        var syncing = synchronization.begin(ready, ready.version());
        assertThrows(DataSourceStateException.class, () -> synchronization.complete(
                syncing,
                revision(ready),
                new SyncArtifactId(240000000000000002L),
                normalizedContent(2),
                NOW));
    }

    private static GovernedDataSource readySource() {
        return GovernedDataSource.create(
                        TENANT,
                        SPACE,
                        SOURCE_ID,
                        GovernedDataSourceType.OBJECT_STORAGE,
                        "平台对象集合",
                        CREATOR,
                        NOW)
                .startProbe(0)
                .completeProbe(1);
    }

    private static SourceRevision revision(GovernedDataSource source) {
        return SourceRevision.objectStorage(
                source,
                new ObjectStorageObjectCollection(
                        new PlatformObjectRangeId("rng_objectstorage1234"),
                        1,
                        List.of(
                                object("obj_ObjectStorage0001", "a"),
                                object("obj_ObjectStorage0002", "b"))),
                CREATOR,
                NOW);
    }

    private static PlatformObjectReference object(String objectId, String digestCharacter) {
        return new PlatformObjectReference(
                objectId,
                digestCharacter.repeat(64),
                12,
                TextContentReference.UTF8_TEXT,
                NOW);
    }

    private static TextContentReference normalizedContent(int version) {
        return new TextContentReference(
                version,
                "foundation-v1/tenants/1/knowledge-data/artifacts/object-storage-" + version + ".txt",
                "c".repeat(64),
                12,
                TextContentReference.UTF8_TEXT,
                NOW);
    }
}

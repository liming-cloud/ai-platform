package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GovernedDataSourceTest {

    private static final TenantId TENANT_ID = new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE_ID = new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE_ID = new DataSourceId(220000000000000002L);
    private static final CreatorId CREATOR_ID = new CreatorId(4921825928971026434L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void 文件数据源必须完成探测和同步后才重新进入就绪状态() {
        var source = GovernedDataSource.create(
                TENANT_ID,
                SPACE_ID,
                SOURCE_ID,
                GovernedDataSourceType.FILE,
                "门店商品文件",
                CREATOR_ID,
                CREATED_AT);

        var probing = source.startProbe(0);
        var ready = probing.completeProbe(1);
        var syncing = ready.startSynchronization(2);
        var synchronizedSource = syncing.completeSynchronization(3);

        assertEquals(GovernedDataSourceStatus.DRAFT, source.status());
        assertEquals(GovernedDataSourceStatus.PROBING, probing.status());
        assertEquals(GovernedDataSourceStatus.READY, ready.status());
        assertEquals(GovernedDataSourceStatus.SYNCING, syncing.status());
        assertEquals(GovernedDataSourceStatus.READY, synchronizedSource.status());
        assertEquals(4, synchronizedSource.version());
    }

    @Test
    void 平台已保存私有CSV的结构化数据集登记后直接就绪并可同步() {
        var source = GovernedDataSource.createReadyStructuredDataset(
                TENANT_ID,
                SPACE_ID,
                SOURCE_ID,
                "客服知识库",
                CREATOR_ID,
                CREATED_AT);

        var syncing = source.startSynchronization(0);

        assertEquals(GovernedDataSourceType.STRUCTURED_DATASET, source.type());
        assertEquals(GovernedDataSourceStatus.READY, source.status());
        assertEquals(0, source.version());
        assertEquals(GovernedDataSourceStatus.SYNCING, syncing.status());
    }

    @Test
    void 过期修订和未探测数据源不能开始同步() {
        var source = fileSource();

        assertThrows(DataSourceStateException.class, () -> source.startSynchronization(0));
        assertThrows(DataSourceStateException.class, () -> source.startProbe(1));
    }

    @Test
    void 退役后不允许重新探测或同步() {
        var retired = fileSource().retire(0);

        assertEquals(GovernedDataSourceStatus.RETIRED, retired.status());
        assertThrows(DataSourceStateException.class, () -> retired.startProbe(1));
        assertThrows(DataSourceStateException.class, () -> retired.startSynchronization(1));
    }

    @Test
    void 只有就绪来源可以登记显式产物发布且保留就绪状态() {
        var ready = fileSource().startProbe(0).completeProbe(1);

        var published = ready.publishArtifact(2);

        assertEquals(GovernedDataSourceStatus.READY, published.status());
        assertEquals(3, published.version());
        assertThrows(DataSourceStateException.class, () -> fileSource().publishArtifact(0));
    }

    @Test
    void 探测或同步失败只可按原失败操作恢复() {
        var source = fileSource();

        var probeFailed = source.startProbe(0).failProbe(1);
        var retriedProbe = probeFailed.retry(2);
        var synchronizationFailed = source.startProbe(0)
                .completeProbe(1)
                .startSynchronization(2)
                .failSynchronization(3);
        var retriedSynchronization = synchronizationFailed.retry(4);

        assertEquals(GovernedDataSourceStatus.FAILED, probeFailed.status());
        assertEquals(DataSourceOperation.PROBE, probeFailed.failedOperation());
        assertEquals(GovernedDataSourceStatus.PROBING, retriedProbe.status());
        assertEquals(GovernedDataSourceStatus.FAILED, synchronizationFailed.status());
        assertEquals(DataSourceOperation.SYNCHRONIZATION, synchronizationFailed.failedOperation());
        assertEquals(GovernedDataSourceStatus.SYNCING, retriedSynchronization.status());
        assertThrows(DataSourceStateException.class, () -> retriedProbe.retry(3));
    }

    private static GovernedDataSource fileSource() {
        return GovernedDataSource.create(
                TENANT_ID,
                SPACE_ID,
                SOURCE_ID,
                GovernedDataSourceType.FILE,
                "门店商品文件",
                CREATOR_ID,
                CREATED_AT);
    }
}

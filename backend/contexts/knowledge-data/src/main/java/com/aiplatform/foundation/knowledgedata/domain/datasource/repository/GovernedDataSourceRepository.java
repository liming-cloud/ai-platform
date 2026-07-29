package com.aiplatform.foundation.knowledgedata.domain.datasource.repository;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.PublishedSyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/**
 * 受治理数据源及其不可变同步事实的事务边界。
 *
 * <p>所有查询都以租户和知识空间为边界；产物与质量结论必须由同一事务提交，禁止留下可被索引消费的半成品。</p>
 */
public interface GovernedDataSourceRepository {

    void register(GovernedDataSource source, SourceRevision revision);

    /**
     * 在来源仍处于同步状态时，原子登记产物、质量结论并完成同步。
     *
     * @throws com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException
     * 当前来源状态或修订不允许完成同步时抛出。
     */
    GovernedDataSource completeSynchronization(
            SyncArtifact artifact,
            QualityReport report,
            long expectedVersion);

    /**
     * 原子登记质量非阻断的发布产物，并递增来源修订。
     */
    GovernedDataSource publishArtifact(PublishedSyncArtifact publication, long expectedVersion);

    /**
     * 以来源修订执行一次乐观状态更新。
     *
     * @return 仅当数据库中的租户、知识空间、标识与预期修订同时匹配时返回 `true`。
     */
    boolean update(GovernedDataSource source, long expectedVersion);

    Optional<GovernedDataSource> findSource(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId dataSourceId);

    Optional<SourceRevision> findRevision(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId dataSourceId,
            long revision);

    Optional<SyncArtifact> findArtifact(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            SyncArtifactId artifactId);

    Optional<QualityReport> findQualityReport(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            SyncArtifactId artifactId);
}

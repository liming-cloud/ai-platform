package com.aiplatform.foundation.knowledgedata.domain.datasource.repository;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
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

    void record(SyncArtifact artifact, QualityReport report);

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

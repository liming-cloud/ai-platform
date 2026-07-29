package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.repository;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceOperation;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.QualityStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.GovernedDataSourceEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.QualityReportEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.SourceRevisionEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.SyncArtifactEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.GovernedDataSourceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.QualityReportMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.SourceRevisionMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.SyncArtifactMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 PostgreSQL 原子登记受治理来源、来源修订和同步质量事实。
 *
 * <p>先写入同步产物再校验质量归属，是为了让同一真实事务证明：任何后续事实校验失败都会回滚前序产物，
 * 不会向索引流程留下半成品。</p>
 */
public final class PostgresGovernedDataSourceRepository implements GovernedDataSourceRepository {

    private final GovernedDataSourceMapper sourceMapper;
    private final SourceRevisionMapper revisionMapper;
    private final SyncArtifactMapper artifactMapper;
    private final QualityReportMapper qualityMapper;
    private final TransactionTemplate transactions;

    public PostgresGovernedDataSourceRepository(
            GovernedDataSourceMapper sourceMapper,
            SourceRevisionMapper revisionMapper,
            SyncArtifactMapper artifactMapper,
            QualityReportMapper qualityMapper,
            PlatformTransactionManager transactionManager) {
        this.sourceMapper = Objects.requireNonNull(sourceMapper, "受治理数据源映射器不能为空");
        this.revisionMapper = Objects.requireNonNull(revisionMapper, "来源修订映射器不能为空");
        this.artifactMapper = Objects.requireNonNull(artifactMapper, "同步产物映射器不能为空");
        this.qualityMapper = Objects.requireNonNull(qualityMapper, "质量结论映射器不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public void register(GovernedDataSource source, SourceRevision revision) {
        Objects.requireNonNull(source, "受治理数据源不能为空");
        Objects.requireNonNull(revision, "来源修订不能为空");
        requireSameSource(source, revision);
        transactions.executeWithoutResult(status -> {
            sourceMapper.insert(toEntity(source));
            revisionMapper.insert(toEntity(revision));
        });
    }

    @Override
    public GovernedDataSource completeSynchronization(
            SyncArtifact artifact,
            QualityReport report,
            long expectedVersion) {
        Objects.requireNonNull(artifact, "同步产物不能为空");
        Objects.requireNonNull(report, "质量结论不能为空");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("预期来源修订不能为负数");
        }
        var result = transactions.execute(status -> {
            var row = sourceMapper.findByScopeForUpdate(
                    artifact.tenantId().value(),
                    artifact.knowledgeSpaceId().value(),
                    artifact.dataSourceId().value());
            if (row == null) {
                throw new DataSourceStateException("同步产物所属数据源不存在或不可访问");
            }
            var current = toDomain(row);
            var completed = current.completeSynchronization(expectedVersion);
            if (revisionMapper.countByScope(
                    artifact.tenantId().value(),
                    artifact.knowledgeSpaceId().value(),
                    artifact.dataSourceId().value(),
                    artifact.sourceRevision()) != 1L) {
                throw new IllegalArgumentException("同步产物引用的来源修订不存在");
            }
            artifactMapper.insert(toEntity(artifact));
            if (!report.artifact().id().equals(artifact.id())) {
                throw new IllegalArgumentException("质量结论必须归属于待登记的同步产物");
            }
            qualityMapper.insert(toEntity(report));
            if (sourceMapper.updateLifecycle(
                    completed.tenantId().value(),
                    completed.knowledgeSpaceId().value(),
                    completed.id().value(),
                    expectedVersion,
                    completed.version(),
                    completed.status().name(),
                    null) != 1) {
                throw new DataSourceStateException("同步完成时数据源修订已变化");
            }
            return completed;
        });
        return Objects.requireNonNull(result, "同步完成事务没有返回数据源");
    }

    @Override
    public boolean update(GovernedDataSource source, long expectedVersion) {
        Objects.requireNonNull(source, "受治理数据源不能为空");
        if (expectedVersion < 0 || source.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("数据源状态更新修订不一致");
        }
        return sourceMapper.updateLifecycle(
                source.tenantId().value(),
                source.knowledgeSpaceId().value(),
                source.id().value(),
                expectedVersion,
                source.version(),
                source.status().name(),
                source.failedOperation() == null ? null : source.failedOperation().name()) == 1;
    }

    @Override
    public Optional<GovernedDataSource> findSource(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId dataSourceId) {
        var row = sourceMapper.selectOne(sourceQuery(tenantId, knowledgeSpaceId, dataSourceId));
        return Optional.ofNullable(row).map(PostgresGovernedDataSourceRepository::toDomain);
    }

    @Override
    public Optional<SourceRevision> findRevision(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId dataSourceId,
            long revision) {
        if (revision < 1) {
            throw new IllegalArgumentException("来源修订必须为正数");
        }
        var row = revisionMapper.findByScope(
                tenantId.value(),
                knowledgeSpaceId.value(),
                dataSourceId.value(),
                revision);
        return Optional.ofNullable(row).map(PostgresGovernedDataSourceRepository::toDomain);
    }

    @Override
    public Optional<SyncArtifact> findArtifact(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            SyncArtifactId artifactId) {
        var row = artifactMapper.selectOne(artifactQuery(tenantId, knowledgeSpaceId, artifactId));
        return Optional.ofNullable(row).map(PostgresGovernedDataSourceRepository::toDomain);
    }

    @Override
    public Optional<QualityReport> findQualityReport(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            SyncArtifactId artifactId) {
        var artifact = findArtifact(tenantId, knowledgeSpaceId, artifactId);
        if (artifact.isEmpty()) {
            return Optional.empty();
        }
        var row = qualityMapper.selectOne(Wrappers.<QualityReportEntity>lambdaQuery()
                .eq(QualityReportEntity::getTenantId, tenantId.value())
                .eq(QualityReportEntity::getKnowledgeSpaceId, knowledgeSpaceId.value())
                .eq(QualityReportEntity::getArtifactId, artifactId.value()));
        return Optional.ofNullable(row).map(value -> toDomain(value, artifact.orElseThrow()));
    }

    private static void requireSameSource(GovernedDataSource source, SourceRevision revision) {
        if (!source.tenantId().equals(revision.tenantId())
                || !source.knowledgeSpaceId().equals(revision.knowledgeSpaceId())
                || !source.id().equals(revision.dataSourceId())
                || source.type() != revision.sourceType()) {
            throw new IllegalArgumentException("来源修订必须属于同一受治理数据源");
        }
    }

    private static com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GovernedDataSourceEntity>
            sourceQuery(TenantId tenantId, KnowledgeSpaceId knowledgeSpaceId, DataSourceId dataSourceId) {
        return Wrappers.<GovernedDataSourceEntity>lambdaQuery()
                .eq(GovernedDataSourceEntity::getTenantId, tenantId.value())
                .eq(GovernedDataSourceEntity::getKnowledgeSpaceId, knowledgeSpaceId.value())
                .eq(GovernedDataSourceEntity::getId, dataSourceId.value());
    }

    private static com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SyncArtifactEntity>
            artifactQuery(TenantId tenantId, KnowledgeSpaceId knowledgeSpaceId, SyncArtifactId artifactId) {
        return Wrappers.<SyncArtifactEntity>lambdaQuery()
                .eq(SyncArtifactEntity::getTenantId, tenantId.value())
                .eq(SyncArtifactEntity::getKnowledgeSpaceId, knowledgeSpaceId.value())
                .eq(SyncArtifactEntity::getId, artifactId.value());
    }

    private static GovernedDataSourceEntity toEntity(GovernedDataSource value) {
        var entity = new GovernedDataSourceEntity();
        entity.setId(value.id().value());
        entity.setTenantId(value.tenantId().value());
        entity.setKnowledgeSpaceId(value.knowledgeSpaceId().value());
        entity.setSourceType(value.type().name());
        entity.setName(value.name());
        entity.setStatus(value.status().name());
        entity.setFailedOperation(
                value.failedOperation() == null ? null : value.failedOperation().name());
        entity.setVersion(value.version());
        entity.setCreatedBy(value.createdBy().value());
        entity.setCreatedAt(value.createdAt());
        return entity;
    }

    private static SourceRevisionEntity toEntity(SourceRevision value) {
        var entity = new SourceRevisionEntity();
        entity.setTenantId(value.tenantId().value());
        entity.setKnowledgeSpaceId(value.knowledgeSpaceId().value());
        entity.setDataSourceId(value.dataSourceId().value());
        entity.setRevision(value.revision());
        entity.setSourceType(value.sourceType().name());
        entity.setDefinitionDigest(value.definitionDigest());
        entity.setCreatedBy(value.createdBy().value());
        entity.setCreatedAt(value.createdAt());
        return entity;
    }

    private static SyncArtifactEntity toEntity(SyncArtifact value) {
        var entity = new SyncArtifactEntity();
        entity.setId(value.id().value());
        entity.setTenantId(value.tenantId().value());
        entity.setKnowledgeSpaceId(value.knowledgeSpaceId().value());
        entity.setDataSourceId(value.dataSourceId().value());
        entity.setSourceRevision(value.sourceRevision());
        entity.setContentDigest(value.contentDigest());
        entity.setRecordCount(value.recordCount());
        entity.setSynchronizedAt(value.synchronizedAt());
        return entity;
    }

    private static QualityReportEntity toEntity(QualityReport value) {
        var artifact = value.artifact();
        var entity = new QualityReportEntity();
        entity.setArtifactId(artifact.id().value());
        entity.setTenantId(artifact.tenantId().value());
        entity.setKnowledgeSpaceId(artifact.knowledgeSpaceId().value());
        entity.setRuleSetVersion(value.ruleSetVersion());
        entity.setStatus(value.status().name());
        entity.setWarningCount(value.warningCount());
        entity.setBlockingCount(value.blockingCount());
        return entity;
    }

    private static GovernedDataSource toDomain(GovernedDataSourceEntity value) {
        return GovernedDataSource.restore(
                new TenantId(value.getTenantId()),
                new KnowledgeSpaceId(value.getKnowledgeSpaceId()),
                new DataSourceId(value.getId()),
                GovernedDataSourceType.valueOf(value.getSourceType()),
                value.getName(),
                new CreatorId(value.getCreatedBy()),
                value.getCreatedAt(),
                GovernedDataSourceStatus.valueOf(value.getStatus()),
                value.getFailedOperation() == null
                        ? null
                        : DataSourceOperation.valueOf(value.getFailedOperation()),
                value.getVersion());
    }

    private static SourceRevision toDomain(SourceRevisionEntity value) {
        return SourceRevision.restore(
                new TenantId(value.getTenantId()),
                new KnowledgeSpaceId(value.getKnowledgeSpaceId()),
                new DataSourceId(value.getDataSourceId()),
                value.getRevision(),
                GovernedDataSourceType.valueOf(value.getSourceType()),
                value.getDefinitionDigest(),
                new CreatorId(value.getCreatedBy()),
                value.getCreatedAt());
    }

    private static SyncArtifact toDomain(SyncArtifactEntity value) {
        return SyncArtifact.create(
                new SyncArtifactId(value.getId()),
                new TenantId(value.getTenantId()),
                new KnowledgeSpaceId(value.getKnowledgeSpaceId()),
                new DataSourceId(value.getDataSourceId()),
                value.getSourceRevision(),
                value.getContentDigest(),
                value.getRecordCount(),
                value.getSynchronizedAt());
    }

    private static QualityReport toDomain(QualityReportEntity value, SyncArtifact artifact) {
        return switch (QualityStatus.valueOf(value.getStatus())) {
            case PASS -> QualityReport.passed(artifact, value.getRuleSetVersion());
            case WARN -> QualityReport.warned(
                    artifact,
                    value.getRuleSetVersion(),
                    value.getWarningCount());
            case BLOCK -> QualityReport.blocked(
                    artifact,
                    value.getRuleSetVersion(),
                    value.getWarningCount(),
                    value.getBlockingCount());
        };
    }
}

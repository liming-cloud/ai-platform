package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.repository;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceOperation;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.PublishedSyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.FileSourceObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.ObjectStorageObjectCollection;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectRangeId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvDefinition;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetFieldMapping;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetLogicalType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetRowFilter;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetSensitivityLevel;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.DataSourceSynchronizationCompletion;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.FileSyncArtifactPublication;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.GovernedDataSourceEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.ObjectStorageObjectEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.PublishedSyncArtifactEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.QualityReportEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.SourceRevisionEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.StructuredDatasetFieldEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.SyncArtifactEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.GovernedDataSourceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.ObjectStorageObjectMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.PublishedSyncArtifactMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.QualityReportMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.SourceRevisionMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.SyncArtifactMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.StructuredDatasetFieldMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 PostgreSQL 原子提交受治理数据源领域操作结果。
 *
 * <p>领域服务已完成来源归属、质量和状态机校验；本实现只执行映射、来源锁定、乐观更新和同一事务提交，
 * 任何技术失败都会回滚前序写入，不向索引流程留下半成品。</p>
 */
public final class PostgresGovernedDataSourceRepository implements GovernedDataSourceRepository {

    private final GovernedDataSourceMapper sourceMapper;
    private final SourceRevisionMapper revisionMapper;
    private final ObjectStorageObjectMapper objectStorageObjectMapper;
    private final StructuredDatasetFieldMapper structuredDatasetFieldMapper;
    private final SyncArtifactMapper artifactMapper;
    private final QualityReportMapper qualityMapper;
    private final PublishedSyncArtifactMapper publicationMapper;
    private final TransactionTemplate transactions;

    public PostgresGovernedDataSourceRepository(
            GovernedDataSourceMapper sourceMapper,
            SourceRevisionMapper revisionMapper,
            ObjectStorageObjectMapper objectStorageObjectMapper,
            StructuredDatasetFieldMapper structuredDatasetFieldMapper,
            SyncArtifactMapper artifactMapper,
            QualityReportMapper qualityMapper,
            PublishedSyncArtifactMapper publicationMapper,
            PlatformTransactionManager transactionManager) {
        this.sourceMapper = Objects.requireNonNull(sourceMapper, "受治理数据源映射器不能为空");
        this.revisionMapper = Objects.requireNonNull(revisionMapper, "来源修订映射器不能为空");
        this.objectStorageObjectMapper = Objects.requireNonNull(
                objectStorageObjectMapper, "对象存储来源对象映射器不能为空");
        this.structuredDatasetFieldMapper = Objects.requireNonNull(
                structuredDatasetFieldMapper, "结构化数据集字段映射器不能为空");
        this.artifactMapper = Objects.requireNonNull(artifactMapper, "同步产物映射器不能为空");
        this.qualityMapper = Objects.requireNonNull(qualityMapper, "质量结论映射器不能为空");
        this.publicationMapper = Objects.requireNonNull(publicationMapper, "发布产物映射器不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public void register(GovernedDataSource source, SourceRevision revision) {
        Objects.requireNonNull(source, "受治理数据源不能为空");
        Objects.requireNonNull(revision, "来源修订不能为空");
        transactions.executeWithoutResult(status -> {
            sourceMapper.insert(toEntity(source));
            revisionMapper.insert(toEntity(revision));
            insertObjectStorageObjects(revision);
            insertStructuredDatasetFields(revision);
        });
    }

    @Override
    public void completeSynchronization(DataSourceSynchronizationCompletion completion) {
        var result = Objects.requireNonNull(completion, "文件同步完成结果不能为空");
        transactions.executeWithoutResult(status -> {
            var completed = result.completedSource();
            lockSource(completed);
            if (sourceMapper.updateLifecycle(
                    completed.tenantId().value(),
                    completed.knowledgeSpaceId().value(),
                    completed.id().value(),
                    result.expectedSourceVersion(),
                    completed.version(),
                    completed.status().name(),
                    null) != 1) {
                throw new DataSourceStateException("同步完成时数据源修订已变化");
            }
            artifactMapper.insert(toEntity(result.artifact()));
            qualityMapper.insert(toEntity(result.qualityReport()));
        });
    }

    @Override
    public void publishArtifact(FileSyncArtifactPublication publication) {
        var result = Objects.requireNonNull(publication, "文件同步产物发布结果不能为空");
        transactions.executeWithoutResult(status -> {
            var published = result.publishedSource();
            lockSource(published);
            if (sourceMapper.updateLifecycle(
                    published.tenantId().value(),
                    published.knowledgeSpaceId().value(),
                    published.id().value(),
                    result.expectedSourceVersion(),
                    published.version(),
                    published.status().name(),
                    null) != 1) {
                throw new DataSourceStateException("发布产物时数据源修订已变化");
            }
            publicationMapper.insert(toEntity(result.publication(), published.version()));
        });
    }

    @Override
    public boolean update(GovernedDataSource source, long expectedVersion) {
        Objects.requireNonNull(source, "受治理数据源不能为空");
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
        return Optional.ofNullable(row).map(value -> toDomain(
                value,
                objectStorageObjectMapper.findByScope(
                        tenantId.value(), knowledgeSpaceId.value(), dataSourceId.value(), revision),
                structuredDatasetFieldMapper.findByScope(
                        tenantId.value(), knowledgeSpaceId.value(), dataSourceId.value(), revision)));
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

    private void lockSource(GovernedDataSource source) {
        var row = sourceMapper.findByScopeForUpdate(
                source.tenantId().value(), source.knowledgeSpaceId().value(), source.id().value());
        if (row == null) {
            throw new DataSourceStateException("受治理数据源不存在或不可访问");
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
        entity.setCurrentRevision(value.currentRevision());
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
        var file = value.fileObject();
        var objects = value.objectStorageObjects();
        var dataset = value.structuredDataset();
        entity.setObjectKey(file == null ? null : file.objectKey());
        entity.setContentDigest(file == null ? null : file.contentDigest());
        entity.setByteSize(file == null ? null : file.byteSize());
        entity.setMediaType(file == null ? null : file.mediaType());
        if (dataset != null) {
            entity.setObjectKey(dataset.csv().objectKey());
            entity.setContentDigest(dataset.csv().contentDigest());
            entity.setByteSize(dataset.csv().byteSize());
            entity.setMediaType(StructuredDatasetCsvReference.UTF8_CSV);
        }
        entity.setObjectRangeId(objects == null ? null : objects.rangeId().value());
        entity.setObjectSelectionRevision(objects == null ? null : objects.selectionRevision());
        entity.setObjectCollectionDigest(objects == null ? null : objects.definitionDigest());
        entity.setDatasetMappingVersion(dataset == null ? null : dataset.mappingVersion());
        entity.setDatasetContentField(dataset == null ? null : dataset.contentField());
        entity.setDatasetTitleField(dataset == null ? null : dataset.titleField());
        entity.setDatasetRowFilter(dataset == null ? null : dataset.rowFilter().name());
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
        entity.setContentVersion(value.content().version());
        entity.setContentObjectKey(value.content().objectKey());
        entity.setContentByteSize(value.content().byteSize());
        entity.setContentMediaType(value.content().mediaType());
        entity.setContentCreatedAt(value.content().createdAt());
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

    private static PublishedSyncArtifactEntity toEntity(
            PublishedSyncArtifact value, long sourceVersion) {
        var artifact = value.artifact();
        var entity = new PublishedSyncArtifactEntity();
        entity.setTenantId(artifact.tenantId().value());
        entity.setKnowledgeSpaceId(artifact.knowledgeSpaceId().value());
        entity.setDataSourceId(artifact.dataSourceId().value());
        entity.setSourceVersion(sourceVersion);
        entity.setArtifactId(artifact.id().value());
        entity.setQualityStatus(value.qualityStatus().name());
        entity.setRuleSetVersion(value.ruleSetVersion());
        entity.setPublishedAt(value.publishedAt());
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
                value.getCurrentRevision(),
                value.getVersion());
    }

    private static SourceRevision toDomain(
            SourceRevisionEntity value,
            java.util.List<ObjectStorageObjectEntity> objectRows,
            java.util.List<StructuredDatasetFieldEntity> datasetFieldRows) {
        var objects = objectStorageObjects(value, objectRows);
        var dataset = structuredDataset(value, datasetFieldRows);
        return SourceRevision.restore(
                new TenantId(value.getTenantId()),
                new KnowledgeSpaceId(value.getKnowledgeSpaceId()),
                new DataSourceId(value.getDataSourceId()),
                value.getRevision(),
                GovernedDataSourceType.valueOf(value.getSourceType()),
                value.getDefinitionDigest(),
                fileObject(value),
                objects,
                dataset,
                new CreatorId(value.getCreatedBy()),
                value.getCreatedAt());
    }

    private static FileSourceObjectReference fileObject(SourceRevisionEntity value) {
        if (GovernedDataSourceType.valueOf(value.getSourceType()) != GovernedDataSourceType.FILE) {
            return null;
        }
        return new FileSourceObjectReference(
                value.getObjectKey(),
                value.getContentDigest(),
                value.getByteSize(),
                value.getMediaType());
    }

    private static ObjectStorageObjectCollection objectStorageObjects(
            SourceRevisionEntity value, java.util.List<ObjectStorageObjectEntity> objectRows) {
        if (GovernedDataSourceType.valueOf(value.getSourceType()) != GovernedDataSourceType.OBJECT_STORAGE) {
            if (!objectRows.isEmpty()) {
                throw new IllegalStateException("非对象存储来源修订不能包含冻结对象");
            }
            return null;
        }
        var collection = new ObjectStorageObjectCollection(
                new PlatformObjectRangeId(value.getObjectRangeId()),
                Objects.requireNonNull(value.getObjectSelectionRevision(), "对象范围选择修订不能为空"),
                objectRows.stream()
                        .map(row -> new PlatformObjectReference(
                                row.getPlatformObjectId(),
                                row.getContentDigest(),
                                row.getByteSize(),
                                row.getMediaType(),
                                row.getFreshAt()))
                        .toList());
        if (!collection.definitionDigest().equals(value.getObjectCollectionDigest())) {
            throw new IllegalStateException("对象存储来源对象集合摘要不一致");
        }
        return collection;
    }

    private static StructuredDatasetCsvDefinition structuredDataset(
            SourceRevisionEntity value,
            java.util.List<StructuredDatasetFieldEntity> fieldRows) {
        if (GovernedDataSourceType.valueOf(value.getSourceType()) != GovernedDataSourceType.STRUCTURED_DATASET) {
            if (!fieldRows.isEmpty()) {
                throw new IllegalStateException("非结构化数据集来源修订不能包含冻结字段映射");
            }
            return null;
        }
        return new StructuredDatasetCsvDefinition(
                new StructuredDatasetCsvReference(
                        value.getObjectKey(), value.getContentDigest(), value.getByteSize()),
                value.getDatasetMappingVersion(),
                value.getDatasetContentField(),
                value.getDatasetTitleField(),
                fieldRows.stream()
                        .map(row -> new StructuredDatasetFieldMapping(
                                row.getFieldName(),
                                StructuredDatasetLogicalType.valueOf(row.getLogicalType()),
                                StructuredDatasetSensitivityLevel.valueOf(row.getSensitivityLevel())))
                        .toList(),
                StructuredDatasetRowFilter.valueOf(value.getDatasetRowFilter()));
    }

    private void insertObjectStorageObjects(SourceRevision revision) {
        var collection = revision.objectStorageObjects();
        if (collection == null) {
            return;
        }
        for (var ordinal = 0; ordinal < collection.objects().size(); ordinal++) {
            var object = collection.objects().get(ordinal);
            var entity = new ObjectStorageObjectEntity();
            entity.setTenantId(revision.tenantId().value());
            entity.setKnowledgeSpaceId(revision.knowledgeSpaceId().value());
            entity.setDataSourceId(revision.dataSourceId().value());
            entity.setRevision(revision.revision());
            entity.setObjectOrdinal(ordinal);
            entity.setPlatformObjectId(object.objectId());
            entity.setContentDigest(object.contentDigest());
            entity.setByteSize(object.byteSize());
            entity.setMediaType(object.mediaType());
            entity.setFreshAt(object.freshAt());
            objectStorageObjectMapper.insert(entity);
        }
    }

    private void insertStructuredDatasetFields(SourceRevision revision) {
        var dataset = revision.structuredDataset();
        if (dataset == null) {
            return;
        }
        for (var ordinal = 0; ordinal < dataset.fields().size(); ordinal++) {
            var field = dataset.fields().get(ordinal);
            var entity = new StructuredDatasetFieldEntity();
            entity.setTenantId(revision.tenantId().value());
            entity.setKnowledgeSpaceId(revision.knowledgeSpaceId().value());
            entity.setDataSourceId(revision.dataSourceId().value());
            entity.setRevision(revision.revision());
            entity.setFieldOrdinal(ordinal);
            entity.setFieldName(field.fieldName());
            entity.setLogicalType(field.logicalType().name());
            entity.setSensitivityLevel(field.sensitivityLevel().name());
            structuredDatasetFieldMapper.insert(entity);
        }
    }

    private static SyncArtifact toDomain(SyncArtifactEntity value) {
        return SyncArtifact.create(
                new SyncArtifactId(value.getId()),
                new TenantId(value.getTenantId()),
                new KnowledgeSpaceId(value.getKnowledgeSpaceId()),
                new DataSourceId(value.getDataSourceId()),
                value.getSourceRevision(),
                new TextContentReference(
                        value.getContentVersion(),
                        value.getContentObjectKey(),
                        value.getContentDigest(),
                        value.getContentByteSize(),
                        value.getContentMediaType(),
                        value.getContentCreatedAt()),
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

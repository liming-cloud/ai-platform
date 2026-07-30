package com.aiplatform.foundation.knowledgedata.domain.datasource.revision;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * 不可变的数据源定义修订。
 *
 * <p>定义摘要是类型化来源定义的稳定指纹，不能以可变连接配置或原始凭据替代；同步产物只能引用已登记的修订。</p>
 */
public final class SourceRevision {

    private final TenantId tenantId;
    private final KnowledgeSpaceId knowledgeSpaceId;
    private final DataSourceId dataSourceId;
    private final long revision;
    private final GovernedDataSourceType sourceType;
    private final String definitionDigest;
    private final FileSourceObjectReference fileObject;
    private final ObjectStorageObjectCollection objectStorageObjects;
    private final StructuredDatasetCsvDefinition structuredDataset;
    private final CreatorId createdBy;
    private final Instant createdAt;

    private SourceRevision(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId dataSourceId,
            long revision,
            GovernedDataSourceType sourceType,
            String definitionDigest,
            FileSourceObjectReference fileObject,
            ObjectStorageObjectCollection objectStorageObjects,
            StructuredDatasetCsvDefinition structuredDataset,
            CreatorId createdBy,
            Instant createdAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.knowledgeSpaceId = Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        this.dataSourceId = Objects.requireNonNull(dataSourceId, "数据源标识不能为空");
        if (revision < 1) {
            throw new IllegalArgumentException("来源修订必须为正数");
        }
        this.revision = revision;
        this.sourceType = Objects.requireNonNull(sourceType, "数据源类型不能为空");
        this.definitionDigest = requireDigest(definitionDigest);
        if (sourceType == GovernedDataSourceType.FILE && fileObject == null) {
            throw new IllegalArgumentException("文件来源修订必须冻结文件对象");
        }
        if (sourceType != GovernedDataSourceType.FILE && fileObject != null) {
            throw new IllegalArgumentException("非文件来源不能冻结文件对象");
        }
        if (sourceType == GovernedDataSourceType.OBJECT_STORAGE && objectStorageObjects == null) {
            throw new IllegalArgumentException("对象存储来源修订必须冻结对象集合");
        }
        if (sourceType != GovernedDataSourceType.OBJECT_STORAGE && objectStorageObjects != null) {
            throw new IllegalArgumentException("非对象存储来源不能冻结对象集合");
        }
        if (sourceType == GovernedDataSourceType.STRUCTURED_DATASET && structuredDataset == null) {
            throw new IllegalArgumentException("结构化数据集来源修订必须冻结 CSV 字段映射");
        }
        if (sourceType != GovernedDataSourceType.STRUCTURED_DATASET && structuredDataset != null) {
            throw new IllegalArgumentException("非结构化数据集来源不能冻结 CSV 字段映射");
        }
        if (fileObject != null && !this.definitionDigest.equals(fileObject.definitionDigest())) {
            throw new IllegalArgumentException("文件来源定义摘要与对象事实不一致");
        }
        if (objectStorageObjects != null && !this.definitionDigest.equals(objectStorageObjects.definitionDigest())) {
            throw new IllegalArgumentException("对象存储来源定义摘要与对象集合不一致");
        }
        if (structuredDataset != null && !this.definitionDigest.equals(structuredDataset.definitionDigest())) {
            throw new IllegalArgumentException("结构化数据集来源定义摘要与 CSV 字段映射不一致");
        }
        this.fileObject = fileObject;
        this.objectStorageObjects = objectStorageObjects;
        this.structuredDataset = structuredDataset;
        this.createdBy = Objects.requireNonNull(createdBy, "创建成员不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    /**
     * 为受治理数据源创建新的不可变定义修订。
     */
    public static SourceRevision file(
            GovernedDataSource source,
            FileSourceObjectReference fileObject,
            CreatorId createdBy,
            Instant createdAt) {
        Objects.requireNonNull(source, "受治理数据源不能为空");
        if (source.type() != GovernedDataSourceType.FILE) {
            throw new IllegalArgumentException("文件对象只能登记到文件来源");
        }
        return new SourceRevision(
                source.tenantId(),
                source.knowledgeSpaceId(),
                source.id(),
                source.currentRevision(),
                source.type(),
                Objects.requireNonNull(fileObject, "文件对象不能为空").definitionDigest(),
                fileObject,
                null,
                null,
                createdBy,
                createdAt);
    }

    /**
     * 为对象存储来源创建新的不可变定义修订。
     */
    public static SourceRevision objectStorage(
            GovernedDataSource source,
            ObjectStorageObjectCollection objectStorageObjects,
            CreatorId createdBy,
            Instant createdAt) {
        Objects.requireNonNull(source, "受治理数据源不能为空");
        if (source.type() != GovernedDataSourceType.OBJECT_STORAGE) {
            throw new IllegalArgumentException("对象集合只能登记到对象存储来源");
        }
        return new SourceRevision(
                source.tenantId(),
                source.knowledgeSpaceId(),
                source.id(),
                source.currentRevision(),
                source.type(),
                Objects.requireNonNull(objectStorageObjects, "对象集合不能为空").definitionDigest(),
                null,
                objectStorageObjects,
                null,
                createdBy,
                createdAt);
    }

    /**
     * 为结构化数据集来源创建新的私有 CSV 与字段映射修订。
     */
    public static SourceRevision structuredDataset(
            GovernedDataSource source,
            StructuredDatasetCsvDefinition structuredDataset,
            CreatorId createdBy,
            Instant createdAt) {
        Objects.requireNonNull(source, "受治理数据源不能为空");
        if (source.type() != GovernedDataSourceType.STRUCTURED_DATASET) {
            throw new IllegalArgumentException("CSV 字段映射只能登记到结构化数据集来源");
        }
        return new SourceRevision(
                source.tenantId(),
                source.knowledgeSpaceId(),
                source.id(),
                source.currentRevision(),
                source.type(),
                Objects.requireNonNull(structuredDataset, "CSV 字段映射不能为空").definitionDigest(),
                null,
                null,
                structuredDataset,
                createdBy,
                createdAt);
    }

    /**
     * 从已验证的持久化事实重建来源修订。
     */
    public static SourceRevision restore(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId dataSourceId,
            long revision,
            GovernedDataSourceType sourceType,
            String definitionDigest,
            FileSourceObjectReference fileObject,
            ObjectStorageObjectCollection objectStorageObjects,
            StructuredDatasetCsvDefinition structuredDataset,
            CreatorId createdBy,
            Instant createdAt) {
        return new SourceRevision(
                tenantId,
                knowledgeSpaceId,
                dataSourceId,
                revision,
                sourceType,
                definitionDigest,
                fileObject,
                objectStorageObjects,
                structuredDataset,
                createdBy,
                createdAt);
    }

    public TenantId tenantId() { return tenantId; }
    public KnowledgeSpaceId knowledgeSpaceId() { return knowledgeSpaceId; }
    public DataSourceId dataSourceId() { return dataSourceId; }
    public long revision() { return revision; }
    public GovernedDataSourceType sourceType() { return sourceType; }
    public String definitionDigest() { return definitionDigest; }

    /**
     * 返回文件来源冻结的对象事实；非文件来源返回 `null`。
     */
    public FileSourceObjectReference fileObject() { return fileObject; }

    /**
     * 返回对象存储来源冻结的范围选择及对象集合；非对象存储来源返回 `null`。
     */
    public ObjectStorageObjectCollection objectStorageObjects() { return objectStorageObjects; }

    /**
     * 返回结构化数据集冻结的私有 CSV 与字段映射；非结构化数据集来源返回 `null`。
     */
    public StructuredDatasetCsvDefinition structuredDataset() { return structuredDataset; }

    public CreatorId createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }

    private static String requireDigest(String value) {
        if (value == null || value.length() != 64
                || value.chars().anyMatch(character -> Character.digit(character, 16) < 0)) {
            throw new IllegalArgumentException("来源定义摘要必须是 64 位十六进制 SHA-256");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}

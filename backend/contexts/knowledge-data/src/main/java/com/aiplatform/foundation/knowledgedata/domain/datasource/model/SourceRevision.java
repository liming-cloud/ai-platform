package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
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
        if (fileObject != null && !this.definitionDigest.equals(fileObject.definitionDigest())) {
            throw new IllegalArgumentException("文件来源定义摘要与对象事实不一致");
        }
        this.fileObject = fileObject;
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
                createdBy,
                createdAt);
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public KnowledgeSpaceId knowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    public DataSourceId dataSourceId() {
        return dataSourceId;
    }

    public long revision() {
        return revision;
    }

    public GovernedDataSourceType sourceType() {
        return sourceType;
    }

    public String definitionDigest() {
        return definitionDigest;
    }

    /**
     * 返回文件来源冻结的对象事实；非文件来源返回 `null`。
     */
    public FileSourceObjectReference fileObject() {
        return fileObject;
    }

    public CreatorId createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireDigest(String value) {
        if (value == null || value.length() != 64
                || value.chars().anyMatch(character -> Character.digit(character, 16) < 0)) {
            throw new IllegalArgumentException("来源定义摘要必须是 64 位十六进制 SHA-256");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}

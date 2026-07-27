package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 受治理 UTF-8 文本数据源聚合。
 */
public final class TextDataSource implements AggregateRoot<DataSourceId> {

    private static final int MAX_NAME_CODE_POINTS = 80;

    private final TenantId tenantId;
    private final KnowledgeSpaceId knowledgeSpaceId;
    private final DataSourceId id;
    private final String name;
    private final DataSourceStatus status;
    private final long version;
    private final int contentVersion;
    private final int publishedContentVersion;
    private final TextContentReference currentContent;
    private final CreatorId createdBy;
    private final Instant createdAt;

    private TextDataSource(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId id,
            String name,
            DataSourceStatus status,
            long version,
            int contentVersion,
            int publishedContentVersion,
            TextContentReference currentContent,
            CreatorId createdBy,
            Instant createdAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.knowledgeSpaceId = Objects.requireNonNull(
                knowledgeSpaceId,
                "知识空间标识不能为空");
        this.id = Objects.requireNonNull(id, "数据源标识不能为空");
        this.name = requireName(name);
        this.status = Objects.requireNonNull(status, "数据源状态不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("数据源版本不能为负数");
        }
        if (contentVersion < 0
                || publishedContentVersion < 0
                || publishedContentVersion > contentVersion) {
            throw new IllegalArgumentException("内容版本关系不正确");
        }
        if ((contentVersion == 0) != (currentContent == null)) {
            throw new IllegalArgumentException("当前内容与内容版本不一致");
        }
        if (currentContent != null && currentContent.version() != contentVersion) {
            throw new IllegalArgumentException("当前内容引用版本不一致");
        }
        if (status == DataSourceStatus.DRAFT && contentVersion != 0) {
            throw new IllegalArgumentException("草稿数据源不能包含内容");
        }
        if (status == DataSourceStatus.CONTENT_READY
                && publishedContentVersion >= contentVersion) {
            throw new IllegalArgumentException("内容就绪状态必须包含尚未发布的新版本");
        }
        if (status == DataSourceStatus.PUBLISHED
                && publishedContentVersion != contentVersion) {
            throw new IllegalArgumentException("发布状态必须指向当前内容版本");
        }
        this.version = version;
        this.contentVersion = contentVersion;
        this.publishedContentVersion = publishedContentVersion;
        this.currentContent = currentContent;
        this.createdBy = Objects.requireNonNull(createdBy, "创建成员不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    public static TextDataSource create(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId id,
            String name,
            CreatorId createdBy,
            Instant createdAt) {
        return new TextDataSource(
                tenantId,
                knowledgeSpaceId,
                id,
                name,
                DataSourceStatus.DRAFT,
                0,
                0,
                0,
                null,
                createdBy,
                createdAt);
    }

    public static TextDataSource reconstitute(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId id,
            String name,
            DataSourceStatus status,
            long version,
            int contentVersion,
            int publishedContentVersion,
            TextContentReference currentContent,
            CreatorId createdBy,
            Instant createdAt) {
        return new TextDataSource(
                tenantId,
                knowledgeSpaceId,
                id,
                name,
                status,
                version,
                contentVersion,
                publishedContentVersion,
                currentContent,
                createdBy,
                createdAt);
    }

    public TextDataSource attach(TextContentReference content) {
        Objects.requireNonNull(content, "文本内容引用不能为空");
        if (content.version() != contentVersion + 1) {
            throw new IllegalArgumentException("内容版本必须连续递增");
        }
        return new TextDataSource(
                tenantId,
                knowledgeSpaceId,
                id,
                name,
                DataSourceStatus.CONTENT_READY,
                version + 1,
                content.version(),
                publishedContentVersion,
                content,
                createdBy,
                createdAt);
    }

    public TextDataSource publish() {
        if (status != DataSourceStatus.CONTENT_READY || currentContent == null) {
            throw new DataSourceStateException("只有内容已就绪的数据源才能发布");
        }
        return new TextDataSource(
                tenantId,
                knowledgeSpaceId,
                id,
                name,
                DataSourceStatus.PUBLISHED,
                version + 1,
                contentVersion,
                contentVersion,
                currentContent,
                createdBy,
                createdAt);
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public KnowledgeSpaceId knowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    public DataSourceId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public DataSourceStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public int contentVersion() {
        return contentVersion;
    }

    public int publishedContentVersion() {
        return publishedContentVersion;
    }

    public TextContentReference currentContent() {
        return currentContent;
    }

    public CreatorId createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireName(String value) {
        Objects.requireNonNull(value, "数据源名称不能为空");
        if (value.isBlank()
                || !value.equals(value.trim())
                || value.codePointCount(0, value.length()) > MAX_NAME_CODE_POINTS) {
            throw new IllegalArgumentException("数据源名称格式不正确");
        }
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (Character.isISOControl(character)
                    || character == '\u2028'
                    || character == '\u2029') {
                throw new IllegalArgumentException("数据源名称不能包含控制或换行字符");
            }
        }
        return value;
    }
}

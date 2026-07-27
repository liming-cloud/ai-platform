package com.aiplatform.foundation.knowledgedata.domain.knowledge.model;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 租户内承载数据源、索引和访问策略的知识边界。
 */
public final class KnowledgeSpace implements AggregateRoot<KnowledgeSpaceId> {

    private static final int MAX_NAME_CODE_POINTS = 80;
    private static final int MAX_DESCRIPTION_CODE_POINTS = 500;

    private final TenantId tenantId;
    private final KnowledgeSpaceId id;
    private final String name;
    private final String description;
    private final KnowledgeSpaceStatus status;
    private final long version;
    private final CreatorId createdBy;
    private final Instant createdAt;

    private KnowledgeSpace(
            TenantId tenantId,
            KnowledgeSpaceId id,
            String name,
            String description,
            KnowledgeSpaceStatus status,
            long version,
            CreatorId createdBy,
            Instant createdAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.id = Objects.requireNonNull(id, "知识空间标识不能为空");
        this.name = requireText(name, "知识空间名称", MAX_NAME_CODE_POINTS, false);
        this.description = requireText(
                description,
                "知识空间说明",
                MAX_DESCRIPTION_CODE_POINTS,
                true);
        this.status = Objects.requireNonNull(status, "知识空间状态不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("知识空间版本不能为负数");
        }
        this.version = version;
        this.createdBy = Objects.requireNonNull(createdBy, "创建成员不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    public static KnowledgeSpace create(
            TenantId tenantId,
            KnowledgeSpaceId id,
            String name,
            String description,
            CreatorId createdBy,
            Instant createdAt) {
        return new KnowledgeSpace(
                tenantId,
                id,
                name,
                description,
                KnowledgeSpaceStatus.DRAFT,
                0,
                createdBy,
                createdAt);
    }

    public static KnowledgeSpace reconstitute(
            TenantId tenantId,
            KnowledgeSpaceId id,
            String name,
            String description,
            KnowledgeSpaceStatus status,
            long version,
            CreatorId createdBy,
            Instant createdAt) {
        return new KnowledgeSpace(
                tenantId,
                id,
                name,
                description,
                status,
                version,
                createdBy,
                createdAt);
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public KnowledgeSpaceId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public KnowledgeSpaceStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public CreatorId createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireText(
            String value,
            String field,
            int maximumCodePoints,
            boolean nullable) {
        if (value == null) {
            if (nullable) {
                return null;
            }
            throw new NullPointerException(field + "不能为空");
        }
        if (!value.equals(value.trim()) || value.isBlank()) {
            throw new IllegalArgumentException(field + "格式不正确");
        }
        if (value.codePointCount(0, value.length()) > maximumCodePoints) {
            throw new IllegalArgumentException(field + "超过长度限制");
        }
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (Character.isISOControl(character)
                    || character == '\u2028'
                    || character == '\u2029') {
                throw new IllegalArgumentException(field + "不能包含控制或换行字符");
            }
        }
        return value;
    }
}

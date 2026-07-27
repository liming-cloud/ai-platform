package com.aiplatform.foundation.assistantdelivery.domain;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.identity.MembershipId;
import java.time.Instant;
import java.util.Objects;

/**
 * 保存稳定助手身份并分配连续版本号的聚合根。
 */
public final class Assistant {

    private static final int MAX_NAME_CODE_POINTS = 160;

    private final TenantId tenantId;
    private final AssistantId id;
    private final String displayName;
    private final int latestVersionNumber;
    private final long version;
    private final MembershipId createdBy;
    private final Instant createdAt;

    private Assistant(
            TenantId tenantId,
            AssistantId id,
            String displayName,
            int latestVersionNumber,
            long version,
            MembershipId createdBy,
            Instant createdAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.id = Objects.requireNonNull(id, "助手标识不能为空");
        this.displayName = requireName(displayName);
        if (latestVersionNumber < 0 || version < 0) {
            throw new IllegalArgumentException("助手版本计数和修订不能为负数");
        }
        this.latestVersionNumber = latestVersionNumber;
        this.version = version;
        this.createdBy = Objects.requireNonNull(createdBy, "创建成员不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    public static Assistant create(
            TenantId tenantId,
            AssistantId id,
            String displayName,
            MembershipId createdBy,
            Instant createdAt) {
        return new Assistant(
                tenantId,
                id,
                displayName,
                0,
                0,
                createdBy,
                createdAt);
    }

    public static Assistant reconstitute(
            TenantId tenantId,
            AssistantId id,
            String displayName,
            int latestVersionNumber,
            long version,
            MembershipId createdBy,
            Instant createdAt) {
        return new Assistant(
                tenantId,
                id,
                displayName,
                latestVersionNumber,
                version,
                createdBy,
                createdAt);
    }

    public Assistant advanceVersion() {
        return new Assistant(
                tenantId,
                id,
                displayName,
                latestVersionNumber + 1,
                version + 1,
                createdBy,
                createdAt);
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public AssistantId id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int latestVersionNumber() {
        return latestVersionNumber;
    }

    public long version() {
        return version;
    }

    public MembershipId createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireName(String value) {
        Objects.requireNonNull(value, "助手名称不能为空");
        if (value.isBlank()
                || !value.equals(value.trim())
                || value.codePointCount(0, value.length()) > MAX_NAME_CODE_POINTS) {
            throw new IllegalArgumentException("助手名称格式不正确");
        }
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (Character.isISOControl(character)
                    || character == '\u2028'
                    || character == '\u2029') {
                throw new IllegalArgumentException("助手名称不能包含控制或换行字符");
            }
        }
        return value;
    }
}

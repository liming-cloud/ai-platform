package com.aiplatform.foundation.modelruntime.domain.model;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderKind;
import com.aiplatform.foundation.security.secret.SecretReference;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * 租户配置的供应商连接。
 *
 * <p>聚合只持有受管密钥引用，绝不持有或返回密钥明文。</p>
 */
public final class ProviderConnection {

    private static final int MAX_NAME_CODE_POINTS = 80;
    private static final int MAX_REGION_CODE_POINTS = 80;

    private final TenantId tenantId;
    private final ProviderConnectionId id;
    private final ProviderKind providerKind;
    private final String displayName;
    private final URI endpoint;
    private final String region;
    private final AuthenticationType authenticationType;
    private final SecretReference credentialReference;
    private final ProviderConnectionStatus status;
    private final long version;
    private final Boolean lastProbeSucceeded;
    private final String lastProbeCode;
    private final Instant lastProbedAt;
    private final Instant createdAt;

    private ProviderConnection(
            TenantId tenantId,
            ProviderConnectionId id,
            ProviderKind providerKind,
            String displayName,
            URI endpoint,
            String region,
            AuthenticationType authenticationType,
            SecretReference credentialReference,
            ProviderConnectionStatus status,
            long version,
            Boolean lastProbeSucceeded,
            String lastProbeCode,
            Instant lastProbedAt,
            Instant createdAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.id = Objects.requireNonNull(id, "供应商连接标识不能为空");
        this.providerKind = Objects.requireNonNull(providerKind, "供应商类型不能为空");
        this.displayName = requireText(
                displayName, "供应商连接名称", MAX_NAME_CODE_POINTS);
        this.endpoint = requireEndpoint(endpoint);
        this.region = requireText(region, "部署区域", MAX_REGION_CODE_POINTS);
        this.authenticationType =
                Objects.requireNonNull(authenticationType, "认证方式不能为空");
        this.credentialReference =
                Objects.requireNonNull(credentialReference, "密钥引用不能为空");
        this.status = Objects.requireNonNull(status, "供应商连接状态不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("供应商连接版本不能为负数");
        }
        if ((lastProbeSucceeded == null) != (lastProbeCode == null)
                || (lastProbeCode == null) != (lastProbedAt == null)) {
            throw new IllegalArgumentException("最近探测结果、编码与时间必须同时存在");
        }
        if (status == ProviderConnectionStatus.DRAFT
                && Boolean.TRUE.equals(lastProbeSucceeded)) {
            throw new IllegalArgumentException("草稿连接不能包含成功探测事实");
        }
        if (status == ProviderConnectionStatus.VERIFIED
                && !Boolean.TRUE.equals(lastProbeSucceeded)) {
            throw new IllegalArgumentException("已验证连接必须包含成功探测事实");
        }
        this.version = version;
        this.lastProbeSucceeded = lastProbeSucceeded;
        this.lastProbeCode = lastProbeCode == null
                ? null
                : requireText(lastProbeCode, "探测结果编码", 120);
        this.lastProbedAt = lastProbedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    public static ProviderConnection create(
            TenantId tenantId,
            ProviderConnectionId id,
            ProviderKind providerKind,
            String displayName,
            URI endpoint,
            String region,
            AuthenticationType authenticationType,
            SecretReference credentialReference,
            Instant createdAt) {
        return new ProviderConnection(
                tenantId,
                id,
                providerKind,
                displayName,
                endpoint,
                region,
                authenticationType,
                credentialReference,
                ProviderConnectionStatus.DRAFT,
                0,
                null,
                null,
                null,
                createdAt);
    }

    public static ProviderConnection reconstitute(
            TenantId tenantId,
            ProviderConnectionId id,
            ProviderKind providerKind,
            String displayName,
            URI endpoint,
            String region,
            AuthenticationType authenticationType,
            SecretReference credentialReference,
            ProviderConnectionStatus status,
            long version,
            Boolean lastProbeSucceeded,
            String lastProbeCode,
            Instant lastProbedAt,
            Instant createdAt) {
        return new ProviderConnection(
                tenantId,
                id,
                providerKind,
                displayName,
                endpoint,
                region,
                authenticationType,
                credentialReference,
                status,
                version,
                lastProbeSucceeded,
                lastProbeCode,
                lastProbedAt,
                createdAt);
    }

    public ProviderConnection recordSuccessfulProbe(String probeCode, Instant probedAt) {
        if (status == ProviderConnectionStatus.DISABLED) {
            throw new ProviderConnectionStateException("已停用的供应商连接不能重新验证");
        }
        return new ProviderConnection(
                tenantId,
                id,
                providerKind,
                displayName,
                endpoint,
                region,
                authenticationType,
                credentialReference,
                ProviderConnectionStatus.VERIFIED,
                version + 1,
                true,
                probeCode,
                Objects.requireNonNull(probedAt, "探测时间不能为空"),
                createdAt);
    }

    public ProviderConnection recordFailedProbe(String probeCode, Instant probedAt) {
        if (status == ProviderConnectionStatus.DISABLED) {
            throw new ProviderConnectionStateException("已停用的供应商连接不能执行探测");
        }
        return new ProviderConnection(
                tenantId,
                id,
                providerKind,
                displayName,
                endpoint,
                region,
                authenticationType,
                credentialReference,
                ProviderConnectionStatus.DRAFT,
                version + 1,
                false,
                probeCode,
                Objects.requireNonNull(probedAt, "探测时间不能为空"),
                createdAt);
    }

    public ProviderConnection disable() {
        if (status == ProviderConnectionStatus.DISABLED) {
            return this;
        }
        return new ProviderConnection(
                tenantId,
                id,
                providerKind,
                displayName,
                endpoint,
                region,
                authenticationType,
                credentialReference,
                ProviderConnectionStatus.DISABLED,
                version + 1,
                lastProbeSucceeded,
                lastProbeCode,
                lastProbedAt,
                createdAt);
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public ProviderConnectionId id() {
        return id;
    }

    public ProviderKind providerKind() {
        return providerKind;
    }

    public String displayName() {
        return displayName;
    }

    public URI endpoint() {
        return endpoint;
    }

    public String region() {
        return region;
    }

    public AuthenticationType authenticationType() {
        return authenticationType;
    }

    public SecretReference credentialReference() {
        return credentialReference;
    }

    public ProviderConnectionStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public String lastProbeCode() {
        return lastProbeCode;
    }

    public Boolean lastProbeSucceeded() {
        return lastProbeSucceeded;
    }

    public Instant lastProbedAt() {
        return lastProbedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static URI requireEndpoint(URI value) {
        Objects.requireNonNull(value, "供应商端点不能为空");
        if (!value.isAbsolute()
                || value.getScheme() == null
                || value.getUserInfo() != null
                || value.getQuery() != null
                || value.getFragment() != null) {
            throw new IllegalArgumentException("供应商端点格式不正确");
        }
        return value;
    }

    private static String requireText(String value, String field, int maximumCodePoints) {
        Objects.requireNonNull(value, field + "不能为空");
        if (value.isBlank()
                || !value.equals(value.trim())
                || value.codePointCount(0, value.length()) > maximumCodePoints) {
            throw new IllegalArgumentException(field + "格式不正确");
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

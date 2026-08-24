package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStateException;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * 平台管理员拥有的受控厂商连接。
 *
 * <p>该聚合始终属于 {@link PlatformModelSupplyScope#PLATFORM}，不携带 `TenantId`。凭据引用必须使用
 * 独立命名空间，轮换会清除与旧修订关联的成功探测事实。</p>
 */
public final class PlatformProviderConnection
        implements AggregateRoot<PlatformProviderConnectionId> {

    private static final int MAX_NAME_CODE_POINTS = 120;
    private static final int MAX_REGION_CODE_POINTS = 80;
    private static final String CREDENTIAL_STORE = "platform-provider-connection";

    private final PlatformModelSupplyScope scope;
    private final PlatformProviderConnectionId id;
    private final ProviderKind providerKind;
    private final PlatformModelDiscoveryProfile discoveryProfile;
    private final String displayName;
    private final URI endpoint;
    private final String region;
    private final AuthenticationType authenticationType;
    private final CredentialReference credentialReference;
    private final ProviderConnectionStatus status;
    private final long version;
    private final Boolean lastProbeSucceeded;
    private final String lastProbeCode;
    private final Instant lastProbedAt;
    private final Instant createdAt;

    private PlatformProviderConnection(
            PlatformModelSupplyScope scope,
            PlatformProviderConnectionId id,
            ProviderKind providerKind,
            PlatformModelDiscoveryProfile discoveryProfile,
            String displayName,
            URI endpoint,
            String region,
            AuthenticationType authenticationType,
            CredentialReference credentialReference,
            ProviderConnectionStatus status,
            long version,
            Boolean lastProbeSucceeded,
            String lastProbeCode,
            Instant lastProbedAt,
            Instant createdAt) {
        this.scope = Objects.requireNonNull(scope, "平台模型供应范围不能为空");
        if (scope != PlatformModelSupplyScope.PLATFORM) {
            throw new IllegalArgumentException("平台供应商连接只能属于平台范围");
        }
        this.id = Objects.requireNonNull(id, "平台供应商连接标识不能为空");
        this.providerKind = Objects.requireNonNull(providerKind, "供应商类型不能为空");
        this.discoveryProfile = Objects.requireNonNull(discoveryProfile, "模型发现协议不能为空");
        if (!discoveryProfile.supports(providerKind)) {
            throw new IllegalArgumentException("模型发现协议与供应商类型不兼容");
        }
        this.displayName = requireText(displayName, "平台供应商连接名称", MAX_NAME_CODE_POINTS);
        this.endpoint = requireEndpoint(endpoint);
        this.region = requireText(region, "部署区域", MAX_REGION_CODE_POINTS);
        this.authenticationType = Objects.requireNonNull(authenticationType, "认证方式不能为空");
        this.credentialReference = requireCredentialReference(credentialReference, id);
        this.status = Objects.requireNonNull(status, "供应商连接状态不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("平台供应商连接版本不能为负数");
        }
        if ((lastProbeSucceeded == null) != (lastProbeCode == null)
                || (lastProbeCode == null) != (lastProbedAt == null)) {
            throw new IllegalArgumentException("最近探测结果、编码与时间必须同时存在");
        }
        if (status == ProviderConnectionStatus.DRAFT && Boolean.TRUE.equals(lastProbeSucceeded)) {
            throw new IllegalArgumentException("草稿平台连接不能包含成功探测事实");
        }
        if (status == ProviderConnectionStatus.VERIFIED && !Boolean.TRUE.equals(lastProbeSucceeded)) {
            throw new IllegalArgumentException("已验证平台连接必须包含成功探测事实");
        }
        this.version = version;
        this.lastProbeSucceeded = lastProbeSucceeded;
        this.lastProbeCode = lastProbeCode == null
                ? null
                : requireText(lastProbeCode, "探测结果编码", 120);
        this.lastProbedAt = lastProbedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    public static PlatformProviderConnection create(
            PlatformProviderConnectionId id,
            ProviderKind providerKind,
            String displayName,
            URI endpoint,
            String region,
            AuthenticationType authenticationType,
            CredentialReference credentialReference,
            Instant createdAt) {
        return create(
                id, providerKind, PlatformModelDiscoveryProfile.legacyDefault(providerKind), displayName,
                endpoint, region, authenticationType, credentialReference, createdAt);
    }

    /** 建立显式冻结权威发现协议的平台连接。 */
    public static PlatformProviderConnection create(
            PlatformProviderConnectionId id,
            ProviderKind providerKind,
            PlatformModelDiscoveryProfile discoveryProfile,
            String displayName,
            URI endpoint,
            String region,
            AuthenticationType authenticationType,
            CredentialReference credentialReference,
            Instant createdAt) {
        return new PlatformProviderConnection(
                PlatformModelSupplyScope.PLATFORM,
                id,
                providerKind,
                discoveryProfile,
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

    public static PlatformProviderConnection reconstitute(
            PlatformProviderConnectionId id,
            ProviderKind providerKind,
            String displayName,
            URI endpoint,
            String region,
            AuthenticationType authenticationType,
            CredentialReference credentialReference,
            ProviderConnectionStatus status,
            long version,
            Boolean lastProbeSucceeded,
            String lastProbeCode,
            Instant lastProbedAt,
            Instant createdAt) {
        return reconstitute(
                id, providerKind, PlatformModelDiscoveryProfile.legacyDefault(providerKind), displayName,
                endpoint, region, authenticationType, credentialReference, status, version,
                lastProbeSucceeded, lastProbeCode, lastProbedAt, createdAt);
    }

    /** 从持久化事实恢复显式发现协议。 */
    public static PlatformProviderConnection reconstitute(
            PlatformProviderConnectionId id,
            ProviderKind providerKind,
            PlatformModelDiscoveryProfile discoveryProfile,
            String displayName,
            URI endpoint,
            String region,
            AuthenticationType authenticationType,
            CredentialReference credentialReference,
            ProviderConnectionStatus status,
            long version,
            Boolean lastProbeSucceeded,
            String lastProbeCode,
            Instant lastProbedAt,
            Instant createdAt) {
        return new PlatformProviderConnection(
                PlatformModelSupplyScope.PLATFORM,
                id,
                providerKind,
                discoveryProfile,
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

    public PlatformProviderConnection recordSuccessfulProbe(String probeCode, Instant probedAt) {
        if (status == ProviderConnectionStatus.DISABLED) {
            throw new ProviderConnectionStateException("已停用的平台供应商连接不能重新验证");
        }
        return revised(
                credentialReference,
                ProviderConnectionStatus.VERIFIED,
                true,
                probeCode,
                Objects.requireNonNull(probedAt, "探测时间不能为空"));
    }

    public PlatformProviderConnection recordFailedProbe(String probeCode, Instant probedAt) {
        if (status == ProviderConnectionStatus.DISABLED) {
            throw new ProviderConnectionStateException("已停用的平台供应商连接不能执行探测");
        }
        return revised(
                credentialReference,
                ProviderConnectionStatus.DRAFT,
                false,
                probeCode,
                Objects.requireNonNull(probedAt, "探测时间不能为空"));
    }

    /**
     * 轮换后的秘密不能复用旧平台连接修订的探测结论。
     *
     * @throws ProviderConnectionStateException 连接已停用时拒绝轮换。
     */
    public PlatformProviderConnection rotateCredential(CredentialReference replacement) {
        if (status == ProviderConnectionStatus.DISABLED) {
            throw new ProviderConnectionStateException("已停用的平台供应商连接不能轮换凭据");
        }
        return revised(requireCredentialReference(replacement, id), ProviderConnectionStatus.DRAFT, null, null, null);
    }

    public PlatformProviderConnection disable() {
        if (status == ProviderConnectionStatus.DISABLED) {
            return this;
        }
        return revised(credentialReference, ProviderConnectionStatus.DISABLED,
                lastProbeSucceeded, lastProbeCode, lastProbedAt);
    }

    public PlatformModelSupplyScope scope() { return scope; }
    @Override public PlatformProviderConnectionId id() { return id; }
    public ProviderKind providerKind() { return providerKind; }
    public PlatformModelDiscoveryProfile discoveryProfile() { return discoveryProfile; }
    public String displayName() { return displayName; }
    public URI endpoint() { return endpoint; }
    public String region() { return region; }
    public AuthenticationType authenticationType() { return authenticationType; }
    public CredentialReference credentialReference() { return credentialReference; }
    public ProviderConnectionStatus status() { return status; }
    public long version() { return version; }
    public Boolean lastProbeSucceeded() { return lastProbeSucceeded; }
    public String lastProbeCode() { return lastProbeCode; }
    public Instant lastProbedAt() { return lastProbedAt; }
    public Instant createdAt() { return createdAt; }

    private PlatformProviderConnection revised(
            CredentialReference nextCredential,
            ProviderConnectionStatus nextStatus,
            Boolean probeSucceeded,
            String probeCode,
            Instant probedAt) {
        return new PlatformProviderConnection(
                scope, id, providerKind, discoveryProfile, displayName, endpoint, region, authenticationType,
                nextCredential, nextStatus, version + 1, probeSucceeded, probeCode, probedAt, createdAt);
    }

    private static CredentialReference requireCredentialReference(
            CredentialReference value, PlatformProviderConnectionId id) {
        value = Objects.requireNonNull(value, "平台私有凭据引用不能为空");
        var uri = URI.create(value.value());
        if (!CREDENTIAL_STORE.equalsIgnoreCase(uri.getHost())
                || !("/" + id.value()).equals(uri.getPath())
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("平台供应商连接凭据引用格式不正确");
        }
        return value;
    }

    private static URI requireEndpoint(URI value) {
        Objects.requireNonNull(value, "供应商端点不能为空");
        if (!value.isAbsolute() || value.getScheme() == null || value.getUserInfo() != null
                || value.getQuery() != null || value.getFragment() != null) {
            throw new IllegalArgumentException("供应商端点格式不正确");
        }
        return value;
    }

    private static String requireText(String value, String field, int maximumCodePoints) {
        Objects.requireNonNull(value, field + "不能为空");
        if (value.isBlank() || !value.equals(value.trim())
                || value.codePointCount(0, value.length()) > maximumCodePoints) {
            throw new IllegalArgumentException(field + "格式不正确");
        }
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (Character.isISOControl(character) || character == '\u2028' || character == '\u2029') {
                throw new IllegalArgumentException(field + "不能包含控制或换行字符");
            }
        }
        return value;
    }
}

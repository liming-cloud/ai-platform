package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception.TenantFeatureErrorCode;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception.TenantOnboardingErrorCode;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantFeatureSettingsResult;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Map;
import java.util.EnumMap;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 平台功能配置用例；授权在读取前完成，变更后同步撤销旧租户会话。 */
public class TenantFeatureSettingsService {
    private final TenantFeatureSettingsStore store;
    private final TenantFeatureSessionRevoker sessions;

    public TenantFeatureSettingsService(TenantFeatureSettingsStore store, TenantFeatureSessionRevoker sessions) {
        this.store = Objects.requireNonNull(store, "功能配置存储不能为空");
        this.sessions = Objects.requireNonNull(sessions, "租户会话撤销器不能为空");
    }

    /** 无权或配置缺失时失败关闭，不编造默认值。 */
    @Transactional(readOnly = true)
    public TenantFeatureSettingsResult get(PlatformRequestContext platform, long tenantId) {
        requireGovernance(platform, tenantId);
        return TenantFeatureSettingsResult.from(requireExisting(tenantId));
    }

    /**
     * 以预期版本完整替换八项功能；会话撤销失败使数据库事务回滚。
     * @throws ApplicationException 无权、租户不存在或版本冲突时拒绝。
     */
    @Transactional
    public TenantFeatureSettingsResult update(PlatformRequestContext platform, long tenantId,
            Map<String, Boolean> features, long expectedVersion) {
        requireGovernance(platform, tenantId);
        if (expectedVersion < 0) throw new IllegalArgumentException("预期版本不能为负数");
        var current = requireExisting(tenantId);
        if (current.version() != expectedVersion) throw new ApplicationException(TenantFeatureErrorCode.VERSION_CONFLICT);
        if (features == null || !features.keySet().equals(TenantFeatureSettingsResult.publishedCodes())
                || features.values().stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("必须提交全部租户功能代码和布尔值");
        }
        var values = new EnumMap<TenantFeature, Boolean>(TenantFeature.class);
        features.forEach((code, enabled) -> values.put(TenantFeature.fromCode(code), enabled));
        var checked = new TenantFeatureSettings(current.tenantId(), values, current.version());
        if (current.availableFeatures().equals(checked.availableFeatures())) return TenantFeatureSettingsResult.from(current);
        var next = new TenantFeatureSettings(current.tenantId(), checked.availableFeatures(), current.version() + 1);
        if (!store.replace(next, current.version())) throw new ApplicationException(TenantFeatureErrorCode.VERSION_CONFLICT);
        sessions.revokeTenantSessions(next.tenantId());
        return TenantFeatureSettingsResult.from(next);
    }

    private TenantFeatureSettings requireExisting(long tenantId) {
        return store.find(tenantId).orElseThrow(() -> new ApplicationException(TenantOnboardingErrorCode.TENANT_NOT_FOUND));
    }

    private static void requireGovernance(PlatformRequestContext platform, long tenantId) {
        if (!Objects.requireNonNull(platform, "平台请求上下文不能为空")
                .hasPermission(PlatformPermissions.TENANT_GOVERNANCE_WRITE)) {
            throw new ApplicationException(TenantOnboardingErrorCode.ACCESS_DENIED);
        }
        if (tenantId <= 0) throw new IllegalArgumentException("租户标识格式不正确");
    }
}

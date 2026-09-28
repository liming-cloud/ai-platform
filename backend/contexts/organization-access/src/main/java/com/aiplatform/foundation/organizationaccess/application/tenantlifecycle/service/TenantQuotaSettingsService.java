package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeQuotaUsage;
import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeQuotaUsageSnapshot;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command.TenantQuotaUpdate;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantQuotaView;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception.TenantOnboardingErrorCode;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception.TenantQuotaErrorCode;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantQuotaSettingsStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantQuota;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import java.math.BigInteger;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 平台配额的独立读用量与保存用例；授权先于任何持久化访问。 */
public class TenantQuotaSettingsService {

    private final TenantQuotaSettingsStore store;
    private final KnowledgeQuotaUsage usage;

    public TenantQuotaSettingsService(TenantQuotaSettingsStore store, KnowledgeQuotaUsage usage) {
        this.store = Objects.requireNonNull(store, "配额存储端口不能为空");
        this.usage = Objects.requireNonNull(usage, "知识用量事实源不能为空");
    }

    /** 读取脱敏用量；不把未计量的来源当作零。 */
    @Transactional(readOnly = true)
    public KnowledgeQuotaUsageSnapshot usage(PlatformRequestContext platform, long tenantId) {
        requireGovernance(platform, tenantId);
        requireExisting(tenantId);
        return usage.snapshot(tenantId);
    }

    /**
     * 原子替换五项配置，仅允许版本一致且不低于保存时已核实用量的值。
     * @throws ApplicationException 无权限、租户缺失、版本过期或下调越界时拒绝。
     */
    @Transactional
    public TenantQuotaView update(PlatformRequestContext platform, long tenantId, TenantQuotaUpdate command) {
        requireGovernance(platform, tenantId);
        command = Objects.requireNonNull(command, "配额保存命令不能为空");
        var current = requireExisting(tenantId);
        if (current.version() != command.expectedVersion()) {
            throw new ApplicationException(TenantQuotaErrorCode.VERSION_CONFLICT);
        }
        var next = new TenantQuota(current.tenantId(), command.knowledgeBases(),
                command.documentsPerKnowledgeBase(), command.bytesPerKnowledgeBase(),
                command.maxFileBytes(), command.maxTextBytes(), current.version() + 1);
        var observed = usage.snapshot(tenantId);
        if (next.knowledgeBases() < observed.knowledgeBases()
                || next.documentsPerKnowledgeBase() < observed.documentsPerKnowledgeBase()
                || BigInteger.valueOf(next.bytesPerKnowledgeBase()).compareTo(observed.bytesPerKnowledgeBase()) < 0) {
            throw new ApplicationException(TenantQuotaErrorCode.BELOW_USAGE);
        }
        if (!observed.complete() && (next.knowledgeBases() < current.knowledgeBases()
                || next.documentsPerKnowledgeBase() < current.documentsPerKnowledgeBase()
                || next.bytesPerKnowledgeBase() < current.bytesPerKnowledgeBase())) {
            throw new ApplicationException(TenantQuotaErrorCode.USAGE_INCOMPLETE);
        }
        if (!store.replace(next, current.version())) {
            throw new ApplicationException(TenantQuotaErrorCode.VERSION_CONFLICT);
        }
        return new TenantQuotaView(next.knowledgeBases(), next.documentsPerKnowledgeBase(),
                Long.toString(next.bytesPerKnowledgeBase()), Long.toString(next.maxFileBytes()),
                Long.toString(next.maxTextBytes()), next.version());
    }

    private TenantQuota requireExisting(long tenantId) {
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

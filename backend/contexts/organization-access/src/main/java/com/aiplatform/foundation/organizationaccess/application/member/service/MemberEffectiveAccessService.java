package com.aiplatform.foundation.organizationaccess.application.member.service;

import com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade;
import com.aiplatform.foundation.organizationaccess.application.member.facade.MemberEffectiveAccess;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccessFacts;
import com.aiplatform.foundation.organizationaccess.application.member.exception.MemberAccountErrorCode;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.organizationaccess.domain.member.model.ConversationFeatures;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import java.util.TreeMap;
import org.springframework.transaction.annotation.Transactional;

/** 每次读取当前资格及功能，不缓存已撤销的权限；查询进程只暴露此只读 facade。 */
@Transactional(readOnly = true)
public class MemberEffectiveAccessService implements MemberAccessFacade {
    private final MemberAccessFacts store;
    private final TenantFeatureSettingsStore ceilings;
    private final PublishedAssistantVersionLookup versions;
    public MemberEffectiveAccessService(MemberAccessFacts store, TenantFeatureSettingsStore ceilings,
            PublishedAssistantVersionLookup versions) { this.store = store; this.ceilings = ceilings; this.versions = versions; }
    @Override public MemberEffectiveAccess read(TrustedRequestContext context) {
        var member = store.find(context.tenantId(), context.membershipId().value()).orElseThrow(MemberEffectiveAccessService::denied);
        if (member.subjectId() != context.subjectId().value() || !member.identityActive()
                || !member.accessReady() || !member.status().equals("ENABLED") || member.permissionVersion() != context.permissionVersion()) throw denied();
        var administrator = member.membershipRole().equals("TENANT_ADMINISTRATOR");
        if (!administrator && (!member.membershipRole().equals("TENANT_USER") || member.features() == null)) throw denied();
        var ceiling = ceilings.find(context.tenantId().value()).orElseThrow(MemberEffectiveAccessService::denied);
        var features = new TreeMap<String, Boolean>();
        for (var code : ConversationFeatures.CODES) features.put(code, administrator
                ? ceiling.allows(TenantFeature.fromCode(code)) : member.features().allows(code, ceiling));
        return new MemberEffectiveAccess(administrator, features, member.assistantIds());
    }
    @Override public void requireFeature(TrustedRequestContext context, String feature) {
        if (!read(context).allows(feature)) throw denied();
    }
    @Override public void requireAssistantVersion(TrustedRequestContext context, long versionId, String feature) {
        var access = read(context);
        if (!access.allows(feature)) throw denied();
        var version = versions.find(context.tenantId(), versionId).orElseThrow(MemberEffectiveAccessService::denied);
        if (!access.tenantAdministrator() && !access.assistantIds().contains(version.assistantId())) throw denied();
    }
    private static ApplicationException denied() { return new ApplicationException(MemberAccountErrorCode.DENIED); }
}

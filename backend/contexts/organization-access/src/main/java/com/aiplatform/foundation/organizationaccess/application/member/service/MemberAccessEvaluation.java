package com.aiplatform.foundation.organizationaccess.application.member.service;

import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberAccountFact;
import com.aiplatform.foundation.organizationaccess.application.member.facade.MemberEffectiveAccess;
import com.aiplatform.foundation.organizationaccess.domain.member.model.ConversationFeatures;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
import java.util.Set;
import java.util.TreeMap;

/** 运行读取和策略预览共用事实交集；预览显式传入候选集合，不写入或替代当前授权。 */
public final class MemberAccessEvaluation {
    private MemberAccessEvaluation() { }
    /** 资格或身份不可用时同时清空功能及助手集合；管理员仍服从租户上限。 */
    public static MemberEffectiveAccess evaluate(MemberAccountFact member, TenantFeatureSettings ceiling, Set<Long> grants) {
        var administrator = member.membershipRole().equals("TENANT_ADMINISTRATOR");
        var ready = member.identityActive() && member.accessReady() && member.status().equals("ENABLED")
                && (administrator || member.membershipRole().equals("TENANT_USER") && member.features() != null);
        var values = new TreeMap<String, Boolean>();
        for (var code : ConversationFeatures.CODES) values.put(code, ready && (administrator
                ? ceiling.allows(TenantFeature.fromCode(code)) : member.features().allows(code, ceiling)));
        return new MemberEffectiveAccess(administrator, values, ready ? Set.copyOf(grants) : Set.of());
    }
}

package com.aiplatform.foundation.organizationaccess.application.member.service;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.member.dto.*;
import com.aiplatform.foundation.organizationaccess.application.member.exception.MemberAccountErrorCode;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccountStore;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberStatusStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.domain.member.model.ConversationFeatures;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/** 四项功能独立保存的事务边界；复用资格锁与撤销端口，不接触助手授权集合。 */
public class MemberFeatureSettingsService {
    /** 完整替换四项功能，两个精确版本保护成员草稿与租户上限。 */
    public record Change(Map<String, Boolean> conversationFeatures, long expectedFeatureVersion, long expectedTenantFeatureVersion) {
        /** 拒绝不完整功能与负版本，不接受客户端指定租户或操作者。 */
        public Change {
            conversationFeatures = new ConversationFeatures(conversationFeatures).values();
            if (expectedFeatureVersion < 0 || expectedTenantFeatureVersion < 0) {
                throw new IllegalArgumentException("功能版本不能为负数");
            }
        }
    }

    private final MemberAccountApplicationService accounts;
    private final MemberAccountStore store;
    private final PlatformAccountStore identities;
    private final TenantMemberStatusStore memberships;
    private final TenantFeatureSettingsStore ceilings;
    private final TenantMemberSessionRevoker sessions;

    public MemberFeatureSettingsService(MemberAccountApplicationService accounts, MemberAccountStore store,
            PlatformAccountStore identities, TenantMemberStatusStore memberships,
            TenantFeatureSettingsStore ceilings, TenantMemberSessionRevoker sessions) {
        this.accounts = accounts; this.store = store; this.identities = identities;
        this.memberships = memberships; this.ceilings = ceilings; this.sessions = sessions;
    }

    /** 仅当前真实租户管理员可读取；目标成员标识不能改变租户范围。 */
    @Transactional(readOnly = true)
    public MemberFeatureSettingsView read(TrustedRequestContext context, long membershipId) {
        accounts.requireAdministrator(context);
        return view(context, fact(context, membershipId));
    }

    /**
     * 按租户、上限、主体、资格顺序锁定；配置、权限修订及无秘密审计同事务提交。
     * 两个版本均先比较，即使请求值未变化也不能接受过时草稿。
     */
    @Transactional
    public MemberFeatureSettingsView save(TrustedRequestContext context, long membershipId, Change command) {
        try {
            store.lockTenant(context.tenantId());
            accounts.requireAdministrator(context);
            store.lockAuthorization(context.tenantId());
            var located = fact(context, membershipId);
            identities.lock(new SubjectId(located.subjectId())).orElseThrow(MemberFeatureSettingsService::denied);
            var membership = memberships.lock(context.tenantId(), new MemberId(membershipId)).orElseThrow(MemberFeatureSettingsService::denied);
            var current = fact(context, membershipId);
            if (!current.membershipRole().equals("TENANT_USER") || !current.accessReady() || current.features() == null) throw denied();
            var ceiling = ceilings.find(context.tenantId().value()).orElseThrow(MemberFeatureSettingsService::denied);
            if (current.featureVersion() != command.expectedFeatureVersion() || ceiling.version() != command.expectedTenantFeatureVersion()) {
                throw new ApplicationException(MemberAccountErrorCode.CONFLICT);
            }
            var changed = new ConversationFeatures(command.conversationFeatures());
            changed.requireChangeWithin(current.features(), ceiling);
            if (changed.equals(current.features())) return view(context, current);
            store.updateFeatures(context.tenantId(), membershipId, changed, current.featureVersion());
            memberships.update(membership.reviseConversationFeatures());
            store.record(context.tenantId(), context.subjectId().value(), "features-" + membershipId + "-" + current.featureVersion(),
                    new MemberAccountOperation("FEATURES", membershipId, fingerprint(command), current.identityVersion()));
            // 仅撤销受管资格；其他租户资格和操作者会话不受此命令影响。撤销失败必须回滚数据库。
            sessions.revokeTenantSessions(new MemberId(membershipId));
            return view(context, fact(context, membershipId));
        } catch (DataAccessException failure) {
            throw new ApplicationException(MemberAccountErrorCode.UNAVAILABLE);
        }
    }

    private MemberAccountFact fact(TrustedRequestContext context, long id) {
        if (id <= 0) throw new IllegalArgumentException("成员标识必须为正整数");
        return store.find(context.tenantId(), id).orElseThrow(MemberFeatureSettingsService::denied);
    }

    private MemberFeatureSettingsView view(TrustedRequestContext context, MemberAccountFact fact) {
        var ceiling = ceilings.find(context.tenantId().value()).orElseThrow(MemberFeatureSettingsService::denied);
        var readOnly = !fact.membershipRole().equals("TENANT_USER");
        if (!readOnly && (!fact.accessReady() || fact.features() == null)) throw denied();
        var available = new TreeMap<String, Boolean>();
        var effective = new TreeMap<String, Boolean>();
        for (var code : ConversationFeatures.CODES) {
            available.put(code, ceiling.allows(TenantFeature.fromCode(code)));
            effective.put(code, !readOnly && fact.identityActive() && fact.status().equals("ENABLED") && fact.features().allows(code, ceiling));
        }
        return new MemberFeatureSettingsView(Long.toString(fact.membershipId()), fact.loginName(), fact.status(), readOnly,
                readOnly ? Map.of() : fact.features().values(), fact.featureVersion(), Map.copyOf(available), ceiling.version(), Map.copyOf(effective));
    }

    private static String fingerprint(Change command) {
        var text = new TreeMap<>(command.conversationFeatures()) + "|" + command.expectedFeatureVersion() + "|" + command.expectedTenantFeatureVersion();
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("无法生成非秘密命令指纹"); }
    }

    private static ApplicationException denied() { return new ApplicationException(MemberAccountErrorCode.DENIED); }
}

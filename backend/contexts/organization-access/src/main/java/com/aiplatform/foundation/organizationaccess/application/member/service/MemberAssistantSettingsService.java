package com.aiplatform.foundation.organizationaccess.application.member.service;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantAuthorizationLookup;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberAccountFact;
import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberAccountOperation;
import com.aiplatform.foundation.organizationaccess.application.member.exception.MemberAccountErrorCode;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccountStore;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberStatusStore;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/** 成员页与策略发布共用单一关系；本页只能替换一个普通资格的助手集合。 */
public class MemberAssistantSettingsService {
    /** 独立助手页的最小安全投影，不返回主体或秘密。 */
    public record MemberAssistantSettingsView(String membershipId, String loginName, String status,
            boolean readOnly, Set<String> assistantIds, long authorizationRevision) { }
    private final MemberAccountApplicationService accounts;
    private final MemberAccountStore store;
    private final PlatformAccountStore identities;
    private final TenantMemberStatusStore memberships;
    private final PublishedAssistantAuthorizationLookup assistants;
    private final TenantMemberSessionRevoker sessions;

    public MemberAssistantSettingsService(MemberAccountApplicationService accounts, MemberAccountStore store,
            PlatformAccountStore identities, TenantMemberStatusStore memberships,
            PublishedAssistantAuthorizationLookup assistants, TenantMemberSessionRevoker sessions) {
        this.accounts = accounts; this.store = store; this.identities = identities;
        this.memberships = memberships; this.assistants = assistants; this.sessions = sessions;
    }

    /** 读取保存集合；管理员目标只读，错租户不确认对象存在。 */
    @Transactional(readOnly = true)
    public MemberAssistantSettingsView read(TrustedRequestContext context, long id) {
        accounts.requireAdministrator(context);
        return view(context, fact(context, id));
    }

    /** 全量替换且先比较 revision；即使集合未变也拒绝过时草稿。 */
    @Transactional
    public MemberAssistantSettingsView save(TrustedRequestContext context, long id, Set<Long> selected, long expectedRevision) {
        if (expectedRevision < 0 || selected == null || selected.stream().anyMatch(value -> value == null || value < 1)) {
            throw new IllegalArgumentException("助手集合及授权版本无效");
        }
        try {
            store.lockTenant(context.tenantId());
            accounts.requireAdministrator(context);
            var revision = store.lockAuthorization(context.tenantId());
            if (revision != expectedRevision) throw new ApplicationException(MemberAccountErrorCode.CONFLICT);
            var located = fact(context, id);
            identities.lock(new SubjectId(located.subjectId())).orElseThrow(MemberAssistantSettingsService::denied);
            var membership = memberships.lock(context.tenantId(), new MemberId(id)).orElseThrow(MemberAssistantSettingsService::denied);
            var current = fact(context, id);
            if (!current.membershipRole().equals("TENANT_USER") || !current.accessReady() || current.features() == null) throw denied();
            if (current.assistantIds().equals(selected)) return view(context, current);
            // 只核对新增关系：已失去发布状态的历史选择仍可被保留或撤销，不能再次授予。
            var additions = new TreeSet<>(selected); additions.removeAll(current.assistantIds());
            for (var assistant : additions) {
                assistants.lock(context.tenantId(), assistant).orElseThrow(MemberAssistantSettingsService::denied);
            }
            store.replaceAssistants(context.tenantId(), id, selected);
            store.advanceAuthorization(context.tenantId(), revision);
            memberships.update(membership.reviseAssistantAuthorization());
            store.record(context.tenantId(), context.subjectId().value(), "assistants-" + id + "-" + revision,
                    new MemberAccountOperation("ASSISTANTS", id, fingerprint(selected, revision), current.identityVersion()));
            sessions.revokeTenantSessions(new MemberId(id));
            return view(context, fact(context, id));
        } catch (DataAccessException failure) { throw new ApplicationException(MemberAccountErrorCode.UNAVAILABLE); }
    }

    private MemberAccountFact fact(TrustedRequestContext context, long id) {
        if (id < 1) throw new IllegalArgumentException("成员引用必须为正整数");
        return store.find(context.tenantId(), id).orElseThrow(MemberAssistantSettingsService::denied);
    }
    private MemberAssistantSettingsView view(TrustedRequestContext context, MemberAccountFact fact) {
        var readOnly = !fact.membershipRole().equals("TENANT_USER");
        if (!readOnly && (!fact.accessReady() || fact.features() == null)) throw denied();
        return new MemberAssistantSettingsView(Long.toString(fact.membershipId()), fact.loginName(), fact.status(), readOnly,
                fact.assistantIds().stream().map(Object::toString).collect(Collectors.toUnmodifiableSet()), store.authorizationRevision(context.tenantId()));
    }
    private static String fingerprint(Set<Long> selected, long revision) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest((new TreeSet<>(selected) + "|" + revision).getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("无法生成非秘密命令指纹"); }
    }
    private static ApplicationException denied() { return new ApplicationException(MemberAccountErrorCode.DENIED); }
}

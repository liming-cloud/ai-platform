package com.aiplatform.foundation.organizationaccess.application.member.service;

import com.aiplatform.foundation.organizationaccess.application.member.dto.*;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccountStore;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberStatusStore;
import com.aiplatform.foundation.organizationaccess.application.member.exception.MemberAccountErrorCode;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordHasher;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordVerifier;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PersonSessionFamilyRevoker;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.assistantdelivery.application.catalog.facade.QueryAssistantCatalogService;
import com.aiplatform.foundation.assistantdelivery.application.catalog.facade.ListQueryAssistantsQuery;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.identity.PermissionCode;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessException;

/** 新身份开户、受限重置和资格治理的唯一外层事务；复用已有资格／角色写入。 */
public class MemberAccountApplicationService {
    private final MemberAccountStore store;
    private final PlatformAccountStore identities;
    private final TenantMemberEstablishmentApplicationService establishment;
    private final TenantMemberStatusApplicationService statuses;
    private final TenantMemberStatusStore membershipLocks;
    private final TenantFeatureSettingsStore ceilings;
    private final QueryAssistantCatalogService assistants;
    private final com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantAuthorizationLookup assistantAuthorization;
    private final PasswordHasher hasher;
    private final PasswordVerifier verifier;
    private final PersonSessionFamilyRevoker sessions;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    public MemberAccountApplicationService(MemberAccountStore store, PlatformAccountStore identities,
            TenantMemberEstablishmentApplicationService establishment, TenantMemberStatusApplicationService statuses,
            TenantMemberStatusStore membershipLocks, TenantFeatureSettingsStore ceilings,
            QueryAssistantCatalogService assistants,
            com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantAuthorizationLookup assistantAuthorization,
            PasswordHasher hasher, PasswordVerifier verifier,
            PersonSessionFamilyRevoker sessions, EntityIdGenerator identifiers, Clock clock) {
        this.store = store; this.identities = identities; this.establishment = establishment;
        this.statuses = statuses; this.membershipLocks = membershipLocks; this.ceilings = ceilings;
        this.assistants = assistants; this.assistantAuthorization = assistantAuthorization; this.hasher = hasher; this.verifier = verifier;
        this.sessions = sessions; this.identifiers = identifiers; this.clock = clock;
    }

    /** 仅真实当前租户管理员可读取开户上限及授权版本。 */
    @Transactional(readOnly = true)
    public MemberCreateOptions options(TrustedRequestContext context) {
        administrator(context);
        var ceiling = ceilings.find(context.tenantId().value()).orElseThrow(MemberAccountApplicationService::denied);
        var features = new TreeMap<String, Boolean>();
        for (var code : com.aiplatform.foundation.organizationaccess.domain.member.model.ConversationFeatures.CODES) {
            features.put(code, ceiling.allows(com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature.fromCode(code)));
        }
        return new MemberCreateOptions(Map.copyOf(features), ceiling.version(), store.authorizationRevision(context.tenantId()));
    }

    /** 只返回当前租户已发布助手，筛选及总数由助手所有者在分页前计算。 */
    @Transactional(readOnly = true)
    public com.aiplatform.foundation.shared.contract.paging.PageResult<MemberAssistantChoice> candidates(
            TrustedRequestContext context, PageQuery page, String q) {
        administrator(context);
        var result = assistants.list(new ListQueryAssistantsQuery(context, page, q, Set.of(), true));
        return com.aiplatform.foundation.shared.contract.paging.PageResult.of(result.items().stream()
                .map(value -> new MemberAssistantChoice(value.assistantId(), value.displayName(),
                        value.assistantVersionId(), value.versionNumber())).toList(), page, result.totalElements());
    }

    /** 账户只读投影；管理员目标不可通过该页获得普通成员写能力。 */
    @Transactional(readOnly = true)
    public MemberAccountView account(TrustedRequestContext context, long membershipId) {
        administrator(context);
        return view(fact(context, membershipId));
    }

    /** 原子提交身份、普通资格、固定角色、配置、助手关系和无秘密审计。 */
    @Transactional
    public MemberAccountView create(TrustedRequestContext context, MemberProvisioningCommand command, char[] password, String key) {
        try {
            key(key);
            store.lockTenant(context.tenantId());
            administrator(context);
            var revision = store.lockAuthorization(context.tenantId());
            var fingerprint = fingerprint(command.loginName() + "|" + new TreeMap<>(command.features())
                    + "|" + new TreeSet<>(command.assistantIds()) + "|" + command.expectedTenantFeatureVersion()
                    + "|" + command.expectedAuthorizationRevision());
            var replay = store.operation(context.tenantId(), context.subjectId().value(), key);
            if (replay.isPresent()) {
                var operation = replay.get();
                if (!operation.operation().equals("CREATE") || !operation.commandFingerprint().equals(fingerprint)) throw conflict();
                var current = fact(context, operation.membershipId());
                var identity = identities.lock(new SubjectId(current.subjectId())).orElseThrow(MemberAccountApplicationService::denied);
                if (!current.membershipRole().equals("TENANT_USER") || !current.status().equals("ENABLED")
                        || !current.identityActive() || !current.accessReady() || current.features() == null
                        || current.version() != 0 || current.featureVersion() != 0
                        || !current.features().values().equals(command.features()) || !current.assistantIds().equals(command.assistantIds())
                        || identity.version() != operation.resultIdentityVersion()
                        || !verifier.matches(password, identity.encodedPassword().verificationValue())) throw conflict();
                return view(current);
            }
            var ceiling = ceilings.find(context.tenantId().value()).orElseThrow(MemberAccountApplicationService::denied);
            if (ceiling.version() != command.expectedTenantFeatureVersion()
                    || revision != command.expectedAuthorizationRevision()) throw conflict();
            var features = new com.aiplatform.foundation.organizationaccess.domain.member.model.ConversationFeatures(command.features());
            features.requireWithin(ceiling);
            // 发布事实与关系写入保持同一事务；拥有者也锁此助手行，不能先读候选再无锁赋权。
            for (var assistant : new TreeSet<>(command.assistantIds())) {
                assistantAuthorization.lock(context.tenantId(), assistant).orElseThrow(MemberAccountApplicationService::denied);
            }
            var identity = SubjectIdentity.register(new SubjectId(identifiers.nextId()), command.loginName(),
                    new EncodedPassword(hasher.encode(password)), clock.instant());
            if (!identities.insert(identity)) throw conflict();
            var member = establishment.establish(context, identity.id().value());
            var id = Long.parseLong(member.membershipId());
            store.initialize(context.tenantId(), id, features, command.assistantIds());
            if (!command.assistantIds().isEmpty()) store.advanceAuthorization(context.tenantId(), revision);
            store.record(context.tenantId(), context.subjectId().value(), key,
                    new MemberAccountOperation("CREATE", id, fingerprint, identity.version()));
            return view(fact(context, id));
        } catch (DataAccessException failure) {
            // 身份写入错误可能携带摘要，禁止原数据库异常链进入统一日志。
            throw new ApplicationException(failure instanceof org.springframework.dao.DuplicateKeyException
                    ? MemberAccountErrorCode.CONFLICT : MemberAccountErrorCode.UNAVAILABLE);
        } finally { Arrays.fill(password, '\0'); }
    }

    /** 共同主体锁内重新裁决唯一归属，禁止重置共享身份或任何管理员。 */
    @Transactional
    public void resetPassword(TrustedRequestContext context, long id, long expectedVersion,
            long expectedIdentityVersion, char[] password, String key) {
        try {
            key(key);
            store.lockTenant(context.tenantId());
            administrator(context);
            var located = fact(context, id);
            var identity = identities.lock(new SubjectId(located.subjectId())).orElseThrow(MemberAccountApplicationService::denied);
            membershipLocks.lock(context.tenantId(), new MemberId(id)).orElseThrow(MemberAccountApplicationService::denied);
            var current = fact(context, id);
            if (!current.passwordResetEligible()) throw denied();
            var fingerprint = fingerprint(id + "|" + expectedVersion + "|" + expectedIdentityVersion);
            var replay = store.operation(context.tenantId(), context.subjectId().value(), key);
            if (replay.isPresent()) {
                var operation = replay.get();
                if (!operation.operation().equals("RESET") || operation.membershipId() != id
                        || !operation.commandFingerprint().equals(fingerprint)
                        || current.version() != expectedVersion || operation.resultIdentityVersion() != identity.version()
                        || !verifier.matches(password, identity.encodedPassword().verificationValue())) throw conflict();
                return;
            }
            if (current.version() != expectedVersion || identity.version() != expectedIdentityVersion) throw conflict();
            var changed = identity.resetPassword(new EncodedPassword(hasher.encode(password)));
            identities.update(changed);
            sessions.revokeAll(identity.id());
            store.record(context.tenantId(), context.subjectId().value(), key,
                    new MemberAccountOperation("RESET", id, fingerprint, changed.version()));
        } catch (DataAccessException failure) {
            throw new ApplicationException(MemberAccountErrorCode.UNAVAILABLE);
        } finally { Arrays.fill(password, '\0'); }
    }

    /** 状态写入只影响当前普通资格；旧版本、管理员及错租户均失败关闭。 */
    @Transactional
    public MemberAccountView status(TrustedRequestContext context, long id, long expectedVersion, boolean enabled) {
        try {
            store.lockTenant(context.tenantId());
            administrator(context);
            var located = fact(context, id);
            identities.lock(new SubjectId(located.subjectId())).orElseThrow(MemberAccountApplicationService::denied);
            membershipLocks.lock(context.tenantId(), new MemberId(id)).orElseThrow(MemberAccountApplicationService::denied);
            var current = fact(context, id);
            if (!current.membershipRole().equals("TENANT_USER") || !current.accessReady()) throw denied();
            if (current.version() != expectedVersion) throw conflict();
            if (current.status().equals(enabled ? "ENABLED" : "DISABLED")) return view(current);
            if (enabled) statuses.enable(context, id); else statuses.disable(context, id);
            // 状态命令每次绑定当前版本；只记录非秘密资格动作，不记录任何会话值。
            store.record(context.tenantId(), context.subjectId().value(), "status-" + id + "-" + expectedVersion + "-" + enabled,
                    new MemberAccountOperation(enabled ? "ENABLE" : "DISABLE", id,
                            fingerprint(id + "|" + expectedVersion + "|" + enabled), current.identityVersion()));
            return view(fact(context, id));
        } catch (DataAccessException failure) {
            throw new ApplicationException(failure instanceof org.springframework.dao.DuplicateKeyException
                    ? MemberAccountErrorCode.CONFLICT : MemberAccountErrorCode.UNAVAILABLE);
        }
    }

    /** 同时检查会话权限和当前数据库固定角色，旧权限集合不能冒充管理员。 */
    @Transactional(readOnly = true)
    public void requireAdministrator(TrustedRequestContext context) { administrator(context); }

    private MemberAccountFact administrator(TrustedRequestContext context) {
        if (!context.hasPermission(new PermissionCode("access:manage"))) throw denied();
        var actor = fact(context, context.membershipId().value());
        if (!actor.membershipRole().equals("TENANT_ADMINISTRATOR") || !actor.status().equals("ENABLED")
                || !actor.accessReady() || !actor.identityActive() || actor.subjectId() != context.subjectId().value()
                || actor.permissionVersion() != context.permissionVersion()) throw denied();
        return actor;
    }
    private MemberAccountFact fact(TrustedRequestContext context, long id) {
        if (id < 1) throw new IllegalArgumentException("成员引用必须为正整数");
        return store.find(context.tenantId(), id).orElseThrow(MemberAccountApplicationService::denied);
    }
    private static MemberAccountView view(MemberAccountFact fact) {
        if (fact.membershipRole().equals("TENANT_USER") && fact.features() == null) throw denied();
        return new MemberAccountView(Long.toString(fact.membershipId()), fact.loginName(), fact.membershipRole(),
                fact.status(), fact.version(), fact.identityVersion(), fact.permissionVersion(), fact.createdAt(),
                fact.features() == null ? Map.of() : fact.features().values(), fact.featureVersion(),
                fact.assistantIds().stream().map(Object::toString).collect(Collectors.toUnmodifiableSet()), fact.passwordResetEligible());
    }
    private static void key(String key) {
        if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("幂等标识格式不合法");
        }
    }
    private static String fingerprint(String command) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(command.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException("命令摘要能力不可用"); }
    }
    private static ApplicationException conflict() { return new ApplicationException(MemberAccountErrorCode.CONFLICT); }
    private static ApplicationException denied() { return new ApplicationException(MemberAccountErrorCode.DENIED); }
}

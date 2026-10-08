package com.aiplatform.foundation.organizationaccess.application.accesspolicy;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantAuthorizationLookup;
import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeSpaceNameLookup;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberAccountFact;
import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberAccountOperation;
import com.aiplatform.foundation.organizationaccess.application.member.exception.MemberAccountErrorCode;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccountStore;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberStatusStore;
import com.aiplatform.foundation.organizationaccess.application.member.service.MemberAccessEvaluation;
import com.aiplatform.foundation.organizationaccess.application.member.service.MemberAccountApplicationService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.domain.accesspolicy.AccessPolicyVersion;
import com.aiplatform.foundation.organizationaccess.domain.member.model.ConversationFeatures;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/** 策略生命周期的组织事务；草稿、预览与发布各自独立，只有发布改变唯一授权关系。 */
public class AccessPolicyService {
    /** 当前页面读取的版本与授权上限；客户端不能自称操作者或租户。 */
    public record Expected(long version, long authorizationRevision, long tenantFeatureVersion) {
        public Expected { if (version < 0 || authorizationRevision < 0 || tenantFeatureVersion < 0) throw new IllegalArgumentException("策略版本不能为负数"); }
    }
    /** 只配置一个已发布助手及普通资格，知识库必须从拥有者事实派生。 */
    public record Selection(long assistantId, Set<Long> membershipIds) {
        public Selection {
            membershipIds = Set.copyOf(membershipIds);
            if (assistantId < 1 || membershipIds.isEmpty() || membershipIds.stream().anyMatch(id -> id < 1)) throw new IllegalArgumentException("请选择已发布助手和普通成员");
        }
    }
    /** 一项运行功能的实际允许及拒绝数量，不代表问答或历史 HTTP 旅程已执行。 */
    public record Check(String feature, int allowCount, int denyCount) { }
    /** 当前草稿包含逐项判定；已发布快照保留发布时汇总，不能回退成当前权限。 */
    public record Preview(boolean passed, int allowCount, int denyCount, int leakCount, List<Check> checks) { }
    /** 已发布助手的最小派生选项，不暴露模型配置、提示词或可编辑知识库引用。 */
    public record AssistantOption(String assistantId, String assistantName, String assistantVersionId, int versionNumber, String knowledgeSpaceName) { }
    /** 版本正文与当前提交前置版本；历史预览只证明其发布时点。 */
    public record View(String policyId, String name, String versionId, int number, String status, long version,
            String assistantId, String assistantName, String knowledgeSpaceName, Set<String> membershipIds,
            List<String> memberNames, long authorizationRevision, long tenantFeatureVersion,
            Preview preview, Long publishedAuthorizationRevision) { }
    private record Facts(long revision, TenantFeatureSettings ceiling, PublishedAssistantAuthorizationLookup.Fact assistant,
            String knowledgeName, List<MemberAccountFact> members, Long previousAssistant) { }
    private record Evaluation(String fingerprint, int allowCount, int denyCount, int leakCount, List<Check> checks) { }

    private final MemberAccountApplicationService accounts;
    private final MemberAccountStore members;
    private final AccessPolicyStore policies;
    private final PlatformAccountStore identities;
    private final TenantMemberStatusStore memberships;
    private final TenantFeatureSettingsStore ceilings;
    private final PublishedAssistantAuthorizationLookup assistants;
    private final KnowledgeSpaceNameLookup knowledge;
    private final TenantMemberSessionRevoker sessions;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    public AccessPolicyService(MemberAccountApplicationService accounts, MemberAccountStore members, AccessPolicyStore policies,
            PlatformAccountStore identities, TenantMemberStatusStore memberships, TenantFeatureSettingsStore ceilings,
            PublishedAssistantAuthorizationLookup assistants, KnowledgeSpaceNameLookup knowledge, TenantMemberSessionRevoker sessions,
            EntityIdGenerator identifiers, Clock clock) {
        this.accounts = accounts; this.members = members; this.policies = policies; this.identities = identities;
        this.memberships = memberships; this.ceilings = ceilings; this.assistants = assistants; this.knowledge = knowledge;
        this.sessions = sessions; this.identifiers = identifiers; this.clock = clock;
    }

    /** 当前租户目录在数据库筛选及分页，不读取其他租户正文。 */
    @Transactional(readOnly = true)
    public PageResult<AccessPolicyStore.Summary> list(TrustedRequestContext context, PageQuery page, String q, String status) {
        accounts.requireAdministrator(context);
        if (q == null || q.length() > 80 || !Set.of("", "DRAFT", "PUBLISHED").contains(status)) throw new IllegalArgumentException("策略目录筛选无效");
        return policies.list(context.tenantId(), page, q.strip(), status);
    }
    /** 授权候选固定为可用普通成员；搜索与总数在数据库分页前完成。 */
    @Transactional(readOnly = true)
    public PageResult<AccessPolicyStore.MemberChoice> memberChoices(TrustedRequestContext context, PageQuery page, String q) {
        accounts.requireAdministrator(context);
        if (q == null || q.length() > 80) throw new IllegalArgumentException("成员搜索长度无效");
        return policies.memberChoices(context.tenantId(), page, q.strip());
    }
    /** 知识库只能由所选助手的发布事实派生；候选不可见时失败关闭。 */
    @Transactional(readOnly = true)
    public AssistantOption assistantOption(TrustedRequestContext context, long id) {
        accounts.requireAdministrator(context);
        var fact = assistants.find(context.tenantId(), id).orElseThrow(AccessPolicyService::denied);
        return new AssistantOption(Long.toString(id), fact.displayName(), Long.toString(fact.assistantVersionId()), fact.versionNumber(),
                knowledge.findName(context.tenantId(), fact.knowledgeSpaceId()).orElseThrow(AccessPolicyService::denied));
    }
    /** 最新版本或指定历史版本；历史预览只证明发布时点，不作为当前授权回退。 */
    @Transactional(readOnly = true)
    public View read(TrustedRequestContext context, long policyId, Long versionId) {
        accounts.requireAdministrator(context);
        var policy = policy(context, policyId);
        var version = versionId == null ? policies.latest(context.tenantId(), policyId).orElseThrow(AccessPolicyService::denied)
                : version(context, policyId, versionId);
        return view(context, policy, version);
    }
    /** 历史版本按版本号倒序分页；不把历史选择解释为当前有效授权。 */
    @Transactional(readOnly = true)
    public PageResult<View> versions(TrustedRequestContext context, long policyId, PageQuery page) {
        accounts.requireAdministrator(context);
        var policy = policy(context, policyId);
        var result = policies.versions(context.tenantId(), policyId, page);
        return PageResult.of(result.items().stream().map(v -> view(context, policy, v)).toList(), page, result.totalElements());
    }

    /** 新建只提交草稿和审计；名称唯一约束裁决重复，不创建有效关系。 */
    @Transactional
    public View create(TrustedRequestContext context, String name, Selection selection, long revision, long featureVersion) {
        try {
            lock(context);
            if (name == null || name.strip().length() < 2 || name.strip().length() > 80) throw new IllegalArgumentException("策略名称须为 2 至 80 字");
            var facts = facts(context, selection, null, true);
            expected(facts, revision, featureVersion);
            var policy = new AccessPolicyStore.Policy(identifiers.nextId(), name.strip());
            var version = draft(identifiers.nextId(), policy.id(), 1, 0, selection, facts);
            policies.create(context.tenantId(), policy); policies.insert(context.tenantId(), version);
            audit(context, version, "CREATE");
            return view(context, policy, version);
        } catch (DataAccessException failure) { throw persistence(failure); }
    }

    /** 一策略最多一个草稿；重复进入已有草稿不重置内容或改变当前授权。 */
    @Transactional
    public View newVersion(TrustedRequestContext context, long policyId, long sourceVersionId, long expectedVersion) {
        try {
            lock(context);
            var policy = policy(context, policyId);
            var latest = policies.latest(context.tenantId(), policyId).orElseThrow(AccessPolicyService::denied);
            var published = policies.published(context.tenantId(), policyId).orElseThrow(AccessPolicyService::conflict);
            if (published.id() != sourceVersionId || published.revision() != expectedVersion) throw conflict();
            if (latest.status().equals("DRAFT")) return view(context, policy, latest);
            var value = new AccessPolicyVersion(identifiers.nextId(), policyId, latest.number() + 1, "DRAFT", 0,
                    published.assistantId(), published.assistantVersionId(), published.assistantName(), published.knowledgeSpaceId(), published.knowledgeSpaceName(),
                    published.membershipIds(), members.authorizationRevision(context.tenantId()), ceiling(context).version(), null, 0, 0, 0, null, null);
            policies.insert(context.tenantId(), value); audit(context, value, "DRAFT");
            return view(context, policy, value);
        } catch (DataAccessException failure) { throw persistence(failure); }
    }
    /** 保存候选并废除旧预览；成员和助手必须来自当前真实事实。 */
    @Transactional
    public View save(TrustedRequestContext context, long policyId, long versionId, Selection selection, Expected expected) {
        try {
            lock(context);
            var policy = policy(context, policyId); var old = editable(context, policyId, versionId, expected.version());
            var facts = facts(context, selection, null, true); expected(facts, expected.authorizationRevision(), expected.tenantFeatureVersion());
            var value = draft(old.id(), policyId, old.number(), old.revision() + 1, selection, facts);
            policies.update(context.tenantId(), value, old.revision()); audit(context, value, "SAVE");
            return view(context, policy, value);
        } catch (DataAccessException failure) { throw persistence(failure); }
    }
    /** 用运行入口同一交集判断当前成员；计数来自事实，不复制原型合成样本或问答结果。 */
    @Transactional
    public View preview(TrustedRequestContext context, long policyId, long versionId, Expected expected) {
        try {
            lock(context);
            var policy = policy(context, policyId); var old = editable(context, policyId, versionId, expected.version());
            var selection = new Selection(old.assistantId(), old.membershipIds());
            var previous = policies.published(context.tenantId(), policyId).map(AccessPolicyVersion::assistantId).orElse(null);
            var facts = facts(context, selection, previous, true); expected(facts, expected.authorizationRevision(), expected.tenantFeatureVersion());
            var candidate = draft(old.id(), policyId, old.number(), old.revision() + 1, selection, facts);
            var evaluation = evaluate(candidate, facts);
            var value = withPreview(candidate, evaluation);
            policies.update(context.tenantId(), value, old.revision()); audit(context, value, "PREVIEW");
            return view(context, policy, value);
        } catch (DataAccessException failure) { throw persistence(failure); }
    }
    /** 发布前在同一事务重新锁定所有依赖；旧发布不可重放，撤销失败回滚正文、关系与审计。 */
    @Transactional
    public View publish(TrustedRequestContext context, long policyId, long versionId, Expected expected) {
        try {
            lock(context);
            var policy = policy(context, policyId); var old = editable(context, policyId, versionId, expected.version());
            var previous = policies.published(context.tenantId(), policyId);
            var facts = facts(context, new Selection(old.assistantId(), old.membershipIds()),
                    previous.map(AccessPolicyVersion::assistantId).orElse(null), true);
            expected(facts, expected.authorizationRevision(), expected.tenantFeatureVersion());
            var evaluation = evaluate(old, facts);
            if (evaluation.leakCount() > 0 || old.previewFingerprint() == null || !old.previewFingerprint().equals(evaluation.fingerprint())) throw conflict();
            var changed = new ArrayList<MemberAccountFact>();
            for (var member : facts.members()) {
                var grants = grants(old, facts.previousAssistant(), member);
                if (grants.equals(member.assistantIds())) continue;
                if (!member.accessReady() || member.features() == null) throw denied();
                var membership = memberships.lock(context.tenantId(), new MemberId(member.membershipId())).orElseThrow(AccessPolicyService::denied);
                members.replaceAssistants(context.tenantId(), member.membershipId(), grants);
                memberships.update(membership.reviseAssistantAuthorization());
                members.record(context.tenantId(), context.subjectId().value(),
                        "policy-" + old.id() + "-" + member.membershipId() + "-" + facts.revision(),
                        new MemberAccountOperation("ASSISTANTS", member.membershipId(), digest(new TreeSet<>(grants) + "|" + old.id() + "|" + facts.revision()), member.identityVersion()));
                changed.add(member);
            }
            var nextRevision = facts.revision() + (changed.isEmpty() ? 0 : 1);
            if (!changed.isEmpty()) members.advanceAuthorization(context.tenantId(), facts.revision());
            if (previous.isPresent()) {
                var p = previous.get();
                policies.update(context.tenantId(), published(p, "SUPERSEDED", p.revision(), p.publishedAuthorizationRevision()), p.revision());
            }
            var value = published(old, "PUBLISHED", old.revision() + 1, nextRevision);
            policies.update(context.tenantId(), value, old.revision()); audit(context, value, "PUBLISH");
            for (var member : changed) sessions.revokeTenantSessions(new MemberId(member.membershipId()));
            return view(context, policy, value);
        } catch (DataAccessException failure) { throw persistence(failure); }
    }

    private void lock(TrustedRequestContext context) {
        members.lockTenant(context.tenantId()); accounts.requireAdministrator(context); members.lockAuthorization(context.tenantId());
    }
    private Facts facts(TrustedRequestContext context, Selection selection, Long previous, boolean locked) {
        var ordinary = new ArrayList<MemberAccountFact>();
        for (int page = 1; ; page++) {
            var ids = policies.ordinaryMembers(context.tenantId(), new PageQuery(page, 100));
            for (var id : ids) ordinary.add(members.find(context.tenantId(), id).orElseThrow(AccessPolicyService::denied));
            if (ids.size() < 100) break;
        }
        ordinary.sort(Comparator.comparingLong(MemberAccountFact::subjectId).thenComparingLong(MemberAccountFact::membershipId));
        if (locked) {
            // 多主体采用稳定顺序；主体锁后重读，跨租户全局身份变化不能越过预览或发布检查。
            for (var member : ordinary) identities.lock(new SubjectId(member.subjectId())).orElseThrow(AccessPolicyService::denied);
            for (var member : ordinary) memberships.lock(context.tenantId(), new MemberId(member.membershipId())).orElseThrow(AccessPolicyService::denied);
            ordinary.replaceAll(member -> members.find(context.tenantId(), member.membershipId()).orElseThrow(AccessPolicyService::denied));
        }
        var available = ordinary.stream().filter(member -> member.status().equals("ENABLED") && member.identityActive()
                && member.accessReady() && member.features() != null).map(MemberAccountFact::membershipId).collect(Collectors.toSet());
        if (!available.containsAll(selection.membershipIds())) throw denied();
        PublishedAssistantAuthorizationLookup.Fact assistant;
        if (locked) {
            var ids = new TreeSet<Long>(); ids.add(selection.assistantId()); if (previous != null) ids.add(previous);
            PublishedAssistantAuthorizationLookup.Fact selected = null;
            for (var id : ids) {
                var found = assistants.lock(context.tenantId(), id);
                if (id == selection.assistantId()) selected = found.orElseThrow(AccessPolicyService::denied);
            }
            assistant = Objects.requireNonNull(selected);
        } else assistant = assistants.find(context.tenantId(), selection.assistantId()).orElseThrow(AccessPolicyService::denied);
        var name = knowledge.findName(context.tenantId(), assistant.knowledgeSpaceId()).orElseThrow(AccessPolicyService::denied);
        return new Facts(members.authorizationRevision(context.tenantId()), ceiling(context), assistant, name, List.copyOf(ordinary), previous);
    }
    private Evaluation evaluate(AccessPolicyVersion version, Facts facts) {
        var canonical = new StringBuilder().append(version.id()).append('|').append(version.revision()).append('|')
                .append(facts.revision()).append('|').append(facts.ceiling().version()).append('|').append(facts.ceiling().availableFeatures())
                .append('|').append(facts.assistant()).append('|').append(facts.previousAssistant()).append('|').append(new TreeSet<>(version.membershipIds()));
        var allowed = new TreeMap<String, Integer>(); var denied = new TreeMap<String, Integer>();
        for (var feature : ConversationFeatures.CODES) { allowed.put(feature, 0); denied.put(feature, 0); }
        var leaks = 0;
        for (var member : facts.members()) {
            canonical.append('|').append(member.membershipId()).append(':').append(member.subjectId()).append(':').append(member.membershipRole())
                    .append(':').append(member.status()).append(':').append(member.identityActive()).append(':').append(member.identityVersion())
                    .append(':').append(member.version()).append(':').append(member.permissionVersion()).append(':').append(member.accessReady())
                    .append(':').append(member.featureVersion()).append(':').append(member.features() == null ? "missing" : new TreeMap<>(member.features().values()))
                    .append(':').append(new TreeSet<>(member.assistantIds()));
            var access = MemberAccessEvaluation.evaluate(member, facts.ceiling(), grants(version, facts.previousAssistant(), member));
            for (var feature : ConversationFeatures.CODES) {
                var actual = access.allows(feature) && access.assistantIds().contains(version.assistantId());
                // 独立期望覆盖未选成员与功能关闭反例，不能将运行判定直接当作零泄漏证明。
                var expected = version.membershipIds().contains(member.membershipId()) && member.identityActive()
                        && member.accessReady() && member.status().equals("ENABLED") && member.membershipRole().equals("TENANT_USER")
                        && member.features() != null && member.features().allows(feature, facts.ceiling());
                if (actual && !expected) leaks++;
                var counts = actual ? allowed : denied;
                counts.compute(feature, (_, count) -> count + 1);
            }
        }
        var checks = ConversationFeatures.CODES.stream().sorted().map(feature -> new Check(feature, allowed.get(feature), denied.get(feature))).toList();
        return new Evaluation(digest(canonical.toString()), allowed.values().stream().mapToInt(Integer::intValue).sum(),
                denied.values().stream().mapToInt(Integer::intValue).sum(), leaks, checks);
    }
    private static Set<Long> grants(AccessPolicyVersion version, Long previous, MemberAccountFact member) {
        var result = new TreeSet<>(member.assistantIds());
        if (previous != null) result.remove(previous);
        result.remove(version.assistantId());
        if (version.membershipIds().contains(member.membershipId())) result.add(version.assistantId());
        return Set.copyOf(result);
    }
    private View view(TrustedRequestContext context, AccessPolicyStore.Policy policy, AccessPolicyVersion version) {
        Preview preview = null;
        if (version.previewFingerprint() != null) {
            if (!version.status().equals("DRAFT")) preview = new Preview(version.leakCount() == 0, version.allowCount(), version.denyCount(), version.leakCount(), List.of());
            else {
                try {
                    var previous = policies.published(context.tenantId(), policy.id()).map(AccessPolicyVersion::assistantId).orElse(null);
                    var facts = facts(context, new Selection(version.assistantId(), version.membershipIds()), previous, false);
                    var result = evaluate(version, facts);
                    preview = new Preview(result.leakCount() == 0 && version.previewFingerprint().equals(result.fingerprint()), version.allowCount(), version.denyCount(), version.leakCount(), result.checks());
                } catch (ApplicationException stale) { preview = new Preview(false, version.allowCount(), version.denyCount(), version.leakCount(), List.of()); }
            }
        }
        var names = version.membershipIds().stream().sorted().map(id -> members.find(context.tenantId(), id).map(MemberAccountFact::loginName).orElse("不可用成员")).toList();
        return new View(Long.toString(policy.id()), policy.name(), Long.toString(version.id()), version.number(), version.status(), version.revision(),
                Long.toString(version.assistantId()), version.assistantName(), version.knowledgeSpaceName(),
                version.membershipIds().stream().map(Object::toString).collect(Collectors.toUnmodifiableSet()), names,
                members.authorizationRevision(context.tenantId()), ceiling(context).version(), preview, version.publishedAuthorizationRevision());
    }
    private static AccessPolicyVersion draft(long id, long policy, int number, long revision, Selection selected, Facts facts) {
        return new AccessPolicyVersion(id, policy, number, "DRAFT", revision, selected.assistantId(), facts.assistant().assistantVersionId(),
                facts.assistant().displayName(), facts.assistant().knowledgeSpaceId(), facts.knowledgeName(), selected.membershipIds(),
                facts.revision(), facts.ceiling().version(), null, 0, 0, 0, null, null);
    }
    private AccessPolicyVersion withPreview(AccessPolicyVersion v, Evaluation result) {
        return new AccessPolicyVersion(v.id(), v.policyId(), v.number(), v.status(), v.revision(), v.assistantId(), v.assistantVersionId(),
                v.assistantName(), v.knowledgeSpaceId(), v.knowledgeSpaceName(), v.membershipIds(), v.authorizationRevision(), v.tenantFeatureVersion(),
                result.fingerprint(), result.allowCount(), result.denyCount(), result.leakCount(), clock.instant(), null);
    }
    private static AccessPolicyVersion published(AccessPolicyVersion v, String status, long revision, Long publishedRevision) {
        return new AccessPolicyVersion(v.id(), v.policyId(), v.number(), status, revision, v.assistantId(), v.assistantVersionId(),
                v.assistantName(), v.knowledgeSpaceId(), v.knowledgeSpaceName(), v.membershipIds(), v.authorizationRevision(), v.tenantFeatureVersion(),
                v.previewFingerprint(), v.allowCount(), v.denyCount(), v.leakCount(), v.previewedAt(), publishedRevision);
    }
    private AccessPolicyStore.Policy policy(TrustedRequestContext context, long id) { return policies.find(context.tenantId(), id).orElseThrow(AccessPolicyService::denied); }
    private AccessPolicyVersion version(TrustedRequestContext context, long policy, long id) { return policies.version(context.tenantId(), policy, id).orElseThrow(AccessPolicyService::denied); }
    private AccessPolicyVersion editable(TrustedRequestContext context, long policy, long id, long revision) {
        var version = version(context, policy, id);
        if (!version.status().equals("DRAFT") || version.revision() != revision) throw conflict();
        return version;
    }
    private TenantFeatureSettings ceiling(TrustedRequestContext context) { return ceilings.find(context.tenantId().value()).orElseThrow(AccessPolicyService::denied); }
    private static void expected(Facts facts, long revision, long featureVersion) {
        if (revision < 0 || featureVersion < 0 || facts.revision() != revision || facts.ceiling().version() != featureVersion) throw conflict();
    }
    private void audit(TrustedRequestContext context, AccessPolicyVersion version, String action) {
        policies.audit(context.tenantId(), context.subjectId().value(), version.policyId(), version.id(), action, version.revision());
    }
    private static String digest(String text) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("无法生成非秘密授权依赖指纹"); }
    }
    private static ApplicationException denied() { return new ApplicationException(MemberAccountErrorCode.DENIED); }
    private static ApplicationException conflict() { return new ApplicationException(MemberAccountErrorCode.CONFLICT); }
    private static ApplicationException persistence(DataAccessException failure) {
        return new ApplicationException(failure instanceof DuplicateKeyException ? MemberAccountErrorCode.CONFLICT : MemberAccountErrorCode.UNAVAILABLE);
    }
}

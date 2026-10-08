package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import com.aiplatform.foundation.organizationaccess.application.accesspolicy.AccessPolicyStore;
import com.aiplatform.foundation.organizationaccess.domain.accesspolicy.AccessPolicyVersion;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.entity.*;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.*;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.entity.TenantMembershipEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.mapper.TenantMembershipMapper;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** 策略版本与审计加入外层授权事务；此仓储不持有或计算平行 ACL。 */
public final class PostgresAccessPolicyStore implements AccessPolicyStore {
    private final AccessPolicyMapper policies;
    private final AccessPolicyVersionMapper versions;
    private final AccessPolicyVersionMemberMapper selections;
    private final AccessPolicyAuditMapper audits;
    private final TenantMembershipMapper memberships;
    public PostgresAccessPolicyStore(AccessPolicyMapper policies, AccessPolicyVersionMapper versions,
            AccessPolicyVersionMemberMapper selections, AccessPolicyAuditMapper audits, TenantMembershipMapper memberships) {
        this.policies = policies; this.versions = versions; this.selections = selections; this.audits = audits; this.memberships = memberships;
    }
    @Override public PageResult<Summary> list(TenantId tenant, PageQuery page, String q, String status) {
        var rows = policies.findPage(new Page<>(page.pageNumber(), page.pageSize()), tenant.value(), q, status);
        return PageResult.of(rows.getRecords().stream().map(row -> new Summary(row.get("policy_id").toString(),
                (String) row.get("name"), (String) row.get("assistant_name"), (String) row.get("status"),
                row.get("version_id").toString(), ((Number) row.get("version_number")).intValue())).toList(), page, rows.getTotal());
    }
    @Override public PageResult<MemberChoice> memberChoices(TenantId tenant, PageQuery page, String q) {
        var rows = policies.memberChoices(new Page<>(page.pageNumber(), page.pageSize()), tenant.value(), q);
        return PageResult.of(rows.getRecords().stream().map(row -> new MemberChoice(row.get("membership_id").toString(),
                (String) row.get("login_name"))).toList(), page, rows.getTotal());
    }
    @Override public Optional<Policy> find(TenantId tenant, long id) {
        return Optional.ofNullable(policies.selectOne(Wrappers.<AccessPolicyEntity>lambdaQuery()
                .eq(AccessPolicyEntity::getTenantId, tenant.value()).eq(AccessPolicyEntity::getPolicyId, id)))
                .map(row -> new Policy(row.getPolicyId(), row.getName()));
    }
    @Override public void create(TenantId tenant, Policy policy) {
        var row = new AccessPolicyEntity(); row.setTenantId(tenant.value()); row.setPolicyId(policy.id()); row.setName(policy.name());
        requireWrite(policies.insert(row));
    }
    @Override public Optional<AccessPolicyVersion> version(TenantId tenant, long policy, long version) {
        return Optional.ofNullable(versions.selectOne(Wrappers.<AccessPolicyVersionEntity>lambdaQuery()
                .eq(AccessPolicyVersionEntity::getTenantId, tenant.value()).eq(AccessPolicyVersionEntity::getPolicyId, policy)
                .eq(AccessPolicyVersionEntity::getVersionId, version))).map(row -> model(tenant, row));
    }
    @Override public Optional<AccessPolicyVersion> latest(TenantId tenant, long policy) {
        var page = versions(tenant, policy, new PageQuery(1, 1));
        return page.items().stream().findFirst();
    }
    @Override public Optional<AccessPolicyVersion> published(TenantId tenant, long policy) {
        return Optional.ofNullable(versions.selectOne(Wrappers.<AccessPolicyVersionEntity>lambdaQuery()
                .eq(AccessPolicyVersionEntity::getTenantId, tenant.value()).eq(AccessPolicyVersionEntity::getPolicyId, policy)
                .eq(AccessPolicyVersionEntity::getStatus, "PUBLISHED"))).map(row -> model(tenant, row));
    }
    @Override public PageResult<AccessPolicyVersion> versions(TenantId tenant, long policy, PageQuery page) {
        var rows = versions.selectPage(new Page<>(page.pageNumber(), page.pageSize()), Wrappers.<AccessPolicyVersionEntity>lambdaQuery()
                .eq(AccessPolicyVersionEntity::getTenantId, tenant.value()).eq(AccessPolicyVersionEntity::getPolicyId, policy)
                .orderByDesc(AccessPolicyVersionEntity::getVersionNumber));
        return PageResult.of(rows.getRecords().stream().map(row -> model(tenant, row)).toList(), page, rows.getTotal());
    }
    @Override public void insert(TenantId tenant, AccessPolicyVersion value) {
        requireWrite(versions.insert(entity(tenant, value)));
        for (var id : new java.util.TreeSet<>(value.membershipIds())) {
            var row = new AccessPolicyVersionMemberEntity(); row.setTenantId(tenant.value()); row.setPolicyId(value.policyId());
            row.setVersionId(value.id()); row.setMembershipId(id); requireWrite(selections.insert(row));
        }
    }
    @Override public void update(TenantId tenant, AccessPolicyVersion value, long expectedRevision) {
        var row = entity(tenant, value);
        // 可空预览与发布列由 Wrapper 显式赋值，避免实体与 Wrapper 重复设置同一列。
        row.setPreviewFingerprint(null); row.setPreviewedAt(null); row.setPublishedAuthorizationRevision(null);
        var update = Wrappers.<AccessPolicyVersionEntity>lambdaUpdate()
                .eq(AccessPolicyVersionEntity::getTenantId, tenant.value()).eq(AccessPolicyVersionEntity::getPolicyId, value.policyId())
                .eq(AccessPolicyVersionEntity::getVersionId, value.id()).eq(AccessPolicyVersionEntity::getRevision, expectedRevision)
                .set(AccessPolicyVersionEntity::getPreviewFingerprint, value.previewFingerprint())
                .set(AccessPolicyVersionEntity::getPreviewedAt, value.previewedAt())
                .set(AccessPolicyVersionEntity::getPublishedAuthorizationRevision, value.publishedAuthorizationRevision());
        requireWrite(versions.update(row, update));
        selections.delete(Wrappers.<AccessPolicyVersionMemberEntity>lambdaQuery()
                .eq(AccessPolicyVersionMemberEntity::getTenantId, tenant.value()).eq(AccessPolicyVersionMemberEntity::getPolicyId, value.policyId())
                .eq(AccessPolicyVersionMemberEntity::getVersionId, value.id()));
        for (var id : new java.util.TreeSet<>(value.membershipIds())) {
            var selection = new AccessPolicyVersionMemberEntity(); selection.setTenantId(tenant.value()); selection.setPolicyId(value.policyId());
            selection.setVersionId(value.id()); selection.setMembershipId(id); requireWrite(selections.insert(selection));
        }
    }
    @Override public List<Long> ordinaryMembers(TenantId tenant, PageQuery page) {
        return memberships.selectPage(new Page<>(page.pageNumber(), page.pageSize()), Wrappers.<TenantMembershipEntity>lambdaQuery()
                .select(TenantMembershipEntity::getMembershipId).eq(TenantMembershipEntity::getTenantId, tenant.value())
                .eq(TenantMembershipEntity::getMembershipRole, "TENANT_USER").orderByAsc(TenantMembershipEntity::getMembershipId))
                .getRecords().stream().map(TenantMembershipEntity::getMembershipId).toList();
    }
    @Override public void audit(TenantId tenant, long actor, long policy, long version, String action, long revision) {
        var row = new AccessPolicyAuditEntity(); row.setTenantId(tenant.value()); row.setActorSubjectId(actor);
        row.setPolicyId(policy); row.setVersionId(version); row.setAction(action); row.setVersionRevision(revision);
        requireWrite(audits.insert(row));
    }
    private AccessPolicyVersion model(TenantId tenant, AccessPolicyVersionEntity row) {
        var selected = selections.selectList(Wrappers.<AccessPolicyVersionMemberEntity>lambdaQuery()
                .eq(AccessPolicyVersionMemberEntity::getTenantId, tenant.value()).eq(AccessPolicyVersionMemberEntity::getPolicyId, row.getPolicyId())
                .eq(AccessPolicyVersionMemberEntity::getVersionId, row.getVersionId())).stream()
                .map(AccessPolicyVersionMemberEntity::getMembershipId).collect(Collectors.toUnmodifiableSet());
        return new AccessPolicyVersion(row.getVersionId(), row.getPolicyId(), row.getVersionNumber(), row.getStatus(), row.getRevision(),
                row.getAssistantId(), row.getAssistantVersionId(), row.getAssistantName(), row.getKnowledgeSpaceId(), row.getKnowledgeSpaceName(),
                selected, row.getAuthorizationRevision(), row.getTenantFeatureVersion(), row.getPreviewFingerprint(),
                row.getAllowCount(), row.getDenyCount(), row.getLeakCount(), row.getPreviewedAt(), row.getPublishedAuthorizationRevision());
    }
    private static AccessPolicyVersionEntity entity(TenantId tenant, AccessPolicyVersion value) {
        var row = new AccessPolicyVersionEntity(); row.setTenantId(tenant.value()); row.setPolicyId(value.policyId()); row.setVersionId(value.id());
        row.setVersionNumber(value.number()); row.setStatus(value.status()); row.setRevision(value.revision());
        row.setAssistantId(value.assistantId()); row.setAssistantVersionId(value.assistantVersionId()); row.setAssistantName(value.assistantName());
        row.setKnowledgeSpaceId(value.knowledgeSpaceId()); row.setKnowledgeSpaceName(value.knowledgeSpaceName());
        row.setAuthorizationRevision(value.authorizationRevision()); row.setTenantFeatureVersion(value.tenantFeatureVersion());
        row.setPreviewFingerprint(value.previewFingerprint()); row.setAllowCount(value.allowCount()); row.setDenyCount(value.denyCount()); row.setLeakCount(value.leakCount());
        row.setPreviewedAt(value.previewedAt()); row.setPublishedAuthorizationRevision(value.publishedAuthorizationRevision());
        return row;
    }
    private static void requireWrite(int count) { if (count != 1) throw new IllegalStateException("策略事实写入失败"); }
}

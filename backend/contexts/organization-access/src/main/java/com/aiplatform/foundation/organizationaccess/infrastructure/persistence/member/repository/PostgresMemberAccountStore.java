package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberAccountFact;
import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberAccountOperation;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccountStore;
import com.aiplatform.foundation.organizationaccess.domain.member.model.ConversationFeatures;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccountMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 仅转换组织拥有的无秘密数据库事实，所有写入加入调用方的业务事务。 */
public final class PostgresMemberAccountStore extends PostgresMemberAccessFacts implements MemberAccountStore {
    private final MemberAccountMapper mapper;
    public PostgresMemberAccountStore(MemberAccountMapper mapper,
            com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccessMapper facts) {
        super(facts);
        this.mapper = Objects.requireNonNull(mapper);
    }
    @Override public void lockTenant(TenantId tenant) {
        if (mapper.lockTenant(tenant.value()) == null) throw new IllegalArgumentException("租户不可用");
    }
    @Override public long lockAuthorization(TenantId tenant) {
        return required(mapper.lockAuthorization(tenant.value()));
    }
    @Override public long authorizationRevision(TenantId tenant) {
        return required(mapper.authorizationRevision(tenant.value()));
    }
    @Override public Optional<MemberAccountOperation> operation(TenantId tenant, long actor, String key) {
        var row = mapper.operation(tenant.value(), actor, key);
        return row == null ? Optional.empty() : Optional.of(new MemberAccountOperation(
                (String) row.get("operation"), number(row, "membership_id"),
                (String) row.get("command_fingerprint"), number(row, "result_identity_version")));
    }
    @Override public void initialize(TenantId tenant, long member, ConversationFeatures features, Set<Long> assistants) {
        write(mapper.insertFeatures(tenant.value(), member, features.values()));
        for (var assistant : new java.util.TreeSet<>(assistants)) write(mapper.insertAssistant(tenant.value(), member, assistant));
    }
    @Override public void advanceAuthorization(TenantId tenant, long expected) {
        write(mapper.advanceAuthorization(tenant.value(), expected));
    }
    @Override public void record(TenantId tenant, long actor, String key, MemberAccountOperation operation) {
        write(mapper.record(tenant.value(), actor, key, operation.operation(), operation.membershipId(),
                operation.commandFingerprint(), operation.resultIdentityVersion()));
    }
    private static long number(Map<String, Object> row, String field) {
        return ((Number) Objects.requireNonNull(row.get(field), "账户读取事实缺失")).longValue();
    }
    private static long required(Long value) {
        return Objects.requireNonNull(value, "成员授权版本事实缺失");
    }
    private static void write(int count) {
        if (count != 1) throw new IllegalStateException("成员账户事实写入失败");
    }
}

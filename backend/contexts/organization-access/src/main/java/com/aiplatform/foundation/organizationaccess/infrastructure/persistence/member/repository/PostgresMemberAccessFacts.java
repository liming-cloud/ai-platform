package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberAccountFact;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccessFacts;
import com.aiplatform.foundation.organizationaccess.domain.member.model.ConversationFeatures;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccessMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 当前资格的只读适配器，查询进程不注册账户写入 Mapper。 */
public class PostgresMemberAccessFacts implements MemberAccessFacts {
    private final MemberAccessMapper mapper;
    /** 只接收无秘密、无写入映射器。 */
    public PostgresMemberAccessFacts(MemberAccessMapper mapper) { this.mapper = Objects.requireNonNull(mapper); }
    @Override public Optional<MemberAccountFact> find(TenantId tenant, long membership) {
        var row = mapper.find(tenant.value(), membership);
        if (row == null) return Optional.empty();
        var features = row.get("feature_version") == null ? null : new ConversationFeatures(Map.of(
                "assistantSelection", (Boolean) row.get("assistant_selection"),
                "conversation", (Boolean) row.get("conversation"),
                "ownHistory", (Boolean) row.get("own_history"),
                "authorizedCitations", (Boolean) row.get("authorized_citations")));
        var created = row.get("created_at");
        var instant = created instanceof Instant value ? value
                : created instanceof java.sql.Timestamp value ? value.toInstant()
                : ((java.time.OffsetDateTime) created).toInstant();
        return Optional.of(new MemberAccountFact(number(row, "membership_id"), number(row, "subject_id"),
                (String) row.get("login_name"), (String) row.get("membership_role"), (String) row.get("status"),
                "ACTIVE".equals(row.get("identity_status")), number(row, "identity_version"),
                number(row, "version"), number(row, "permission_version"), instant,
                features, features == null ? 0 : number(row, "feature_version"),
                Set.copyOf(mapper.assistants(tenant.value(), membership)), Boolean.TRUE.equals(row.get("password_reset_eligible")), Boolean.TRUE.equals(row.get("access_ready"))));
    }
    private static long number(Map<String, Object> row, String field) {
        return ((Number) Objects.requireNonNull(row.get(field), "账户读取事实缺失")).longValue();
    }
}

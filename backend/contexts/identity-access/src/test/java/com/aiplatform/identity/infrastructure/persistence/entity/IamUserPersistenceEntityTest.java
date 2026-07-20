package com.aiplatform.identity.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.identity.domain.EnterpriseUser;
import com.aiplatform.identity.domain.UserStatus;
import com.aiplatform.identity.domain.port.EnterpriseUserRepository;
import com.aiplatform.identity.infrastructure.persistence.repository.IamUserRepositoryAdapter;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IamUserPersistenceEntityTest {
    @Test
    void mapsBetweenIamUserTableRowAndEnterpriseUserAggregate() {
        Instant createdAt = Instant.parse("2026-05-21T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-05-21T01:00:00Z");
        IamUserPersistenceEntity row = new IamUserPersistenceEntity();
        row.setId(1001L);
        row.setTenantId(2001L);
        row.setUsername("admin");
        row.setDisplayName("平台管理员");
        row.setStatus("ACTIVE");
        row.setCreatedAt(createdAt);
        row.setUpdatedAt(updatedAt);

        EnterpriseUser user = row.toDomain(Set.of("platform_admin", "auditor"));

        assertThat(user.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(user.userId()).isEqualTo("1001");
        assertThat(user.loginName()).isEqualTo("admin");
        assertThat(user.displayName()).isEqualTo("平台管理员");
        assertThat(user.roleCodes()).containsExactlyInAnyOrder("platform_admin", "auditor");
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.createdAt()).isEqualTo(createdAt);
        assertThat(user.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void rejectsMissingDatabaseIdsInsteadOfStringValueNull() {
        IamUserPersistenceEntity row = new IamUserPersistenceEntity();
        row.setUsername("admin");
        row.setDisplayName("平台管理员");
        row.setStatus("ACTIVE");
        row.setCreatedAt(Instant.parse("2026-05-21T00:00:00Z"));
        row.setUpdatedAt(Instant.parse("2026-05-21T01:00:00Z"));

        assertThatThrownBy(() -> row.toDomain(Set.of("platform_admin")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("用户租户 ID不能为空");

        row.setTenantId(2001L);
        assertThatThrownBy(() -> row.toDomain(Set.of("platform_admin")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("用户 ID不能为空");
    }

    @Test
    void mapsEnterpriseUserAggregateToIamUserTableRow() {
        EnterpriseUser user = new EnterpriseUser(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                Set.of("platform_admin"),
                UserStatus.ACTIVE,
                Instant.parse("2026-05-21T00:00:00Z"),
                Instant.parse("2026-05-21T01:00:00Z")
        );

        IamUserPersistenceEntity row = IamUserPersistenceEntity.fromDomain(user);

        assertThat(row.getId()).isEqualTo(1001L);
        assertThat(row.getTenantId()).isEqualTo(2001L);
        assertThat(row.getUsername()).isEqualTo("admin");
        assertThat(row.getDisplayName()).isEqualTo("平台管理员");
        assertThat(row.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void repositoryAdapterImplementsDomainPort() {
        assertThat(EnterpriseUserRepository.class).isAssignableFrom(IamUserRepositoryAdapter.class);
    }
}

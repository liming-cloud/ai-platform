package com.aiplatform.identity.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.identity.domain.PasswordCredential;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IamUserCredentialPersistenceEntityTest {
    @Test
    void mapsActiveCredentialProjectionToDomain() {
        IamUserCredentialPersistenceEntity row = activeCredential();

        PasswordCredential credential = row.toDomain();

        assertThat(credential.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(credential.userId()).isEqualTo("1001");
        assertThat(credential.passwordHash().value()).isEqualTo("pbkdf2-sha256$1000$c2FsdA==$aGFzaA==");
        assertThat(credential.updatedAt()).isEqualTo(Instant.parse("2026-05-21T01:00:00Z"));
    }

    @Test
    void rejectsMissingDatabaseIdsInsteadOfStringValueNull() {
        IamUserCredentialPersistenceEntity row = activeCredential();
        row.setTenantId(null);

        assertThatThrownBy(row::toDomain)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("密码凭证租户 ID不能为空");

        row.setTenantId(2001L);
        row.setId(null);
        assertThatThrownBy(row::toDomain)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("密码凭证用户 ID不能为空");
    }

    private static IamUserCredentialPersistenceEntity activeCredential() {
        IamUserCredentialPersistenceEntity row = new IamUserCredentialPersistenceEntity();
        row.setId(1001L);
        row.setTenantId(2001L);
        row.setPasswordHash("pbkdf2-sha256$1000$c2FsdA==$aGFzaA==");
        row.setUpdatedAt(Instant.parse("2026-05-21T01:00:00Z"));
        return row;
    }
}

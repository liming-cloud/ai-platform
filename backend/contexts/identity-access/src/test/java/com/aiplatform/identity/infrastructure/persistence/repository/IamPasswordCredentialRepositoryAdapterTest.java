package com.aiplatform.identity.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.identity.domain.PasswordCredential;
import com.aiplatform.identity.domain.port.PasswordCredentialRepository;
import com.aiplatform.identity.infrastructure.persistence.entity.IamUserCredentialPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamUserMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class IamPasswordCredentialRepositoryAdapterTest {
    @Test
    void loadsPasswordCredentialFromIamUserTableByTenantAndUser() {
        IamUserCredentialPersistenceEntity row = new IamUserCredentialPersistenceEntity();
        row.setId(1001L);
        row.setTenantId(2001L);
        row.setPasswordHash("pbkdf2-sha256$1000$c2FsdA==$aGFzaA==");
        row.setUpdatedAt(Instant.parse("2026-05-21T01:00:00Z"));
        AtomicBoolean invoked = new AtomicBoolean(false);
        IamUserMapper mapper = mapperReturningCredential(row, invoked);
        PasswordCredentialRepository repository = new IamPasswordCredentialRepositoryAdapter(mapper);

        Optional<PasswordCredential> credential = repository.findByUserId(TenantId.of("2001"), "1001");

        assertThat(credential).isPresent();
        assertThat(credential.get().tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(credential.get().userId()).isEqualTo("1001");
        assertThat(credential.get().passwordHash().value()).isEqualTo("pbkdf2-sha256$1000$c2FsdA==$aGFzaA==");
        assertThat(invoked).isTrue();
    }

    @Test
    void returnsEmptyWhenCredentialRowIsMissing() {
        IamUserMapper mapper = mapperReturningCredential(null, new AtomicBoolean(false));
        PasswordCredentialRepository repository = new IamPasswordCredentialRepositoryAdapter(mapper);

        Optional<PasswordCredential> credential = repository.findByUserId(TenantId.of("2001"), "1001");

        assertThat(credential).isEmpty();
    }

    @Test
    void returnsEmptyWhenPersistedCredentialHashIsInvalid() {
        IamUserCredentialPersistenceEntity row = new IamUserCredentialPersistenceEntity();
        row.setId(1001L);
        row.setTenantId(2001L);
        row.setPasswordHash("pbkdf2-sha256$not-number$c2FsdA==$aGFzaA==");
        row.setUpdatedAt(Instant.parse("2026-05-21T01:00:00Z"));
        IamUserMapper mapper = mapperReturningCredential(row, new AtomicBoolean(false));
        PasswordCredentialRepository repository = new IamPasswordCredentialRepositoryAdapter(mapper);

        Optional<PasswordCredential> credential = repository.findByUserId(TenantId.of("2001"), "1001");

        assertThat(credential).isEmpty();
    }

    private static IamUserMapper mapperReturningCredential(
            IamUserCredentialPersistenceEntity row,
            AtomicBoolean invoked
    ) {
        return (IamUserMapper) Proxy.newProxyInstance(
                IamUserMapper.class.getClassLoader(),
                new Class<?>[]{IamUserMapper.class},
                (proxy, method, args) -> {
                    if ("findActiveCredential".equals(method.getName())) {
                        assertThat(args).containsExactly(2001L, 1001L);
                        invoked.set(true);
                        return row;
                    }
                    throw new UnsupportedOperationException(method.getName() + " is not used by this test");
                }
        );
    }
}

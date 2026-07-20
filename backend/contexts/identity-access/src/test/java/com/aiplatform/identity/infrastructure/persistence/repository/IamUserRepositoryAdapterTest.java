package com.aiplatform.identity.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.domain.EnterpriseUser;
import com.aiplatform.identity.domain.UserStatus;
import com.aiplatform.identity.infrastructure.persistence.entity.IamUserPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamUserMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IamUserRepositoryAdapterTest {
    @Test
    void findByLoginNameUsesActiveTenantUserQuery() {
        IamUserMapper mapper = mock(IamUserMapper.class);
        IamUserRepositoryAdapter repository = new IamUserRepositoryAdapter(mapper);
        IamUserPersistenceEntity row = userRow();
        when(mapper.findActiveTenantUserByLoginName(2001L, "admin")).thenReturn(row);
        when(mapper.findActiveRoleCodes(2001L, 1001L)).thenReturn(Set.of("platform-admin"));

        assertThat(repository.findByLoginName(TenantId.of("2001"), "admin"))
                .contains(user());

        verify(mapper).findActiveTenantUserByLoginName(2001L, "admin");
    }

    @Test
    void findByIdFailsClosedWhenTenantOrUserIsInactive() {
        IamUserMapper mapper = mock(IamUserMapper.class);
        IamUserRepositoryAdapter repository = new IamUserRepositoryAdapter(mapper);
        when(mapper.findActiveTenantUserById(2001L, 1001L)).thenReturn(null);

        assertThat(repository.findById(TenantId.of("2001"), "1001")).isEmpty();

        verify(mapper).findActiveTenantUserById(2001L, 1001L);
        verify(mapper, never()).findActiveRoleCodes(2001L, 1001L);
    }

    @Test
    void saveUpdatesOnlyTenantScopedActiveUser() {
        IamUserMapper mapper = mock(IamUserMapper.class);
        IamUserRepositoryAdapter repository = new IamUserRepositoryAdapter(mapper);
        when(mapper.update(any(IamUserPersistenceEntity.class), anyUpdate())).thenReturn(1);
        when(mapper.findActiveRoleCodes(2001L, 1001L)).thenReturn(Set.of("platform-admin"));

        repository.save(user());

        verify(mapper).update(any(IamUserPersistenceEntity.class), anyUpdate());
        verify(mapper, never()).updateById(any(IamUserPersistenceEntity.class));
    }

    @Test
    void saveFailsClosedWhenTenantScopedUserUpdateIsLost() {
        IamUserMapper mapper = mock(IamUserMapper.class);
        IamUserRepositoryAdapter repository = new IamUserRepositoryAdapter(mapper);
        when(mapper.update(any(IamUserPersistenceEntity.class), anyUpdate())).thenReturn(0);

        assertThatThrownBy(() -> repository.save(user()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("企业用户保存失败");
        verify(mapper, never()).updateById(any(IamUserPersistenceEntity.class));
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<IamUserPersistenceEntity> anyUpdate() {
        return any(Wrapper.class);
    }

    private static EnterpriseUser user() {
        return new EnterpriseUser(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                Set.of("platform-admin"),
                UserStatus.ACTIVE,
                Instant.parse("2026-05-21T08:00:00Z"),
                Instant.parse("2026-05-21T08:05:00Z")
        );
    }

    private static IamUserPersistenceEntity userRow() {
        IamUserPersistenceEntity row = new IamUserPersistenceEntity();
        row.setId(1001L);
        row.setTenantId(2001L);
        row.setUsername("admin");
        row.setDisplayName("平台管理员");
        row.setStatus("ACTIVE");
        row.setCreatedAt(Instant.parse("2026-05-21T08:00:00Z"));
        row.setUpdatedAt(Instant.parse("2026-05-21T08:05:00Z"));
        return row;
    }
}

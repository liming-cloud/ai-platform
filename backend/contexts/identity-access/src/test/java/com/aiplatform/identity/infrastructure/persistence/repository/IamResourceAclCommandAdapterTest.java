package com.aiplatform.identity.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.model.ResourceAclSubject;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IamResourceAclCommandAdapterTest {
    @Test
    void replacesOnlyAfterEverySubjectBelongsToTenant() {
        IamAuthorizationMapper mapper = Mockito.mock(IamAuthorizationMapper.class);
        IamResourceAclCommandAdapter adapter = new IamResourceAclCommandAdapter(mapper);
        when(mapper.countActiveAclSubject(2001L, "user", 1003L)).thenReturn(1);
        when(mapper.countActiveAclSubject(2001L, "role", 3003L)).thenReturn(1);
        when(mapper.insertResourceAcl(2001L, "AI_APPLICATION", 14L, "user", 1003L,
                "app-studio:application:use", 1002L)).thenReturn(1);
        when(mapper.insertResourceAcl(2001L, "AI_APPLICATION", 14L, "role", 3003L,
                "app-studio:application:use", 1002L)).thenReturn(1);

        adapter.replaceAllowingSubjects(
                TenantId.of("2001"),
                "AI_APPLICATION",
                "14",
                new PermissionCode("app-studio:application:use"),
                Set.of(new ResourceAclSubject("user", "1003"), new ResourceAclSubject("role", "3003")),
                "1002"
        );

        verify(mapper).softDeleteResourceAcls(
                2001L, "AI_APPLICATION", 14L, "app-studio:application:use", 1002L
        );
        verify(mapper).insertResourceAcl(
                2001L, "AI_APPLICATION", 14L, "user", 1003L, "app-studio:application:use", 1002L
        );
        verify(mapper).insertResourceAcl(
                2001L, "AI_APPLICATION", 14L, "role", 3003L, "app-studio:application:use", 1002L
        );
    }

    @Test
    void invalidSubjectDoesNotClearExistingAccessPolicy() {
        IamAuthorizationMapper mapper = Mockito.mock(IamAuthorizationMapper.class);
        IamResourceAclCommandAdapter adapter = new IamResourceAclCommandAdapter(mapper);
        when(mapper.countActiveAclSubject(2001L, "role", 9999L)).thenReturn(0);

        assertThatThrownBy(() -> adapter.replaceAllowingSubjects(
                TenantId.of("2001"),
                "AI_APPLICATION",
                "14",
                new PermissionCode("app-studio:application:use"),
                Set.of(new ResourceAclSubject("role", "9999")),
                "1002"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACL 主体不存在或不可用");

        verify(mapper, never()).softDeleteResourceAcls(
                2001L, "AI_APPLICATION", 14L, "app-studio:application:use", 1002L
        );
    }
}

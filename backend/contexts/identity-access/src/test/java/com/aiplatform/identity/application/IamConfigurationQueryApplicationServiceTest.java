package com.aiplatform.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.application.port.IamConfigurationQueryPort;
import com.aiplatform.identity.application.result.IamRoleResult;
import com.aiplatform.identity.application.result.IamUserConfigurationPage;
import com.aiplatform.identity.application.result.IamUserConfigurationResult;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

class IamConfigurationQueryApplicationServiceTest {
    @Test
    void delegatesValidatedTenantUserPaginationToTheQueryPort() {
        IamConfigurationQueryPort port = mock(IamConfigurationQueryPort.class);
        IamUserConfigurationPage expected = new IamUserConfigurationPage(List.of(new IamUserConfigurationResult(
                "1001", "li.ming", "李明", "li.ming@example.com", "ACTIVE", List.of("business-user")
        )), 21, 2, 20);
        when(port.findUsers(TenantId.of("2001"), 2, 20)).thenReturn(expected);
        IamConfigurationQueryApplicationService service = new IamConfigurationQueryApplicationService(port);

        var result = service.listUsers(TenantId.of("2001"), 2, 20);

        org.assertj.core.api.Assertions.assertThat(result).isSameAs(expected);
        verify(port).findUsers(TenantId.of("2001"), 2, 20);
    }

    @Test
    void rejectsInvalidTenantUserPageBeforeCallingThePort() {
        IamConfigurationQueryPort port = mock(IamConfigurationQueryPort.class);
        IamConfigurationQueryApplicationService service = new IamConfigurationQueryApplicationService(port);

        assertThatThrownBy(() -> service.listUsers(TenantId.of("2001"), 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("页码");
        assertThatThrownBy(() -> service.listUsers(TenantId.of("2001"), 1, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("每页条数");
        verify(port, org.mockito.Mockito.never()).findUsers(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()
        );
    }

    @Test
    void iamConfigurationQueryApplicationServiceIsSpringServiceBean() {
        org.assertj.core.api.Assertions.assertThat(IamConfigurationQueryApplicationService.class.getAnnotation(Service.class))
                .isNotNull();
    }

    @Test
    void delegatesTenantScopedRoleReadToPort() {
        IamConfigurationQueryPort port = mock(IamConfigurationQueryPort.class);
        when(port.findRoles(TenantId.of("2001"))).thenReturn(List.of(new IamRoleResult(
                "3001",
                "platform-admin",
                "平台管理员",
                "PLATFORM",
                "admin.platform-overview",
                "ACTIVE",
                null
        )));
        IamConfigurationQueryApplicationService service = new IamConfigurationQueryApplicationService(port);

        var roles = service.listRoles(TenantId.of("2001"));

        org.assertj.core.api.Assertions.assertThat(roles).extracting(IamRoleResult::code)
                .containsExactly("platform-admin");
        verify(port).findRoles(TenantId.of("2001"));
    }

    @Test
    void rejectsBlankRoleIdBeforeQueryingRoleMatrices() {
        IamConfigurationQueryApplicationService service = new IamConfigurationQueryApplicationService(
                mock(IamConfigurationQueryPort.class)
        );

        assertThatThrownBy(() -> service.listRoleMenus(TenantId.of("2001"), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色 ID不能为空");
    }
}

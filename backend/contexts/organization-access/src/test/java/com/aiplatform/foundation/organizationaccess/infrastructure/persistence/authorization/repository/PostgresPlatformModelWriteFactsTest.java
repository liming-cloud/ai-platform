package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformRoleBindingMapper;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostgresPlatformModelWriteFactsTest {
    @Test
    void 旧会话声称管理权限但当前主体不存在时拒绝() {
        var mapper = mock(PlatformRoleBindingMapper.class);
        assertThrows(InvalidAuthenticatedSessionException.class,
                () -> new PostgresPlatformModelWriteFacts(mapper).requireCurrent(context(true)));
    }

    @Test
    void 请求本身没有模型写权限时不读取组织数据() {
        var mapper = mock(PlatformRoleBindingMapper.class);
        assertThrows(InvalidAuthenticatedSessionException.class,
                () -> new PostgresPlatformModelWriteFacts(mapper).requireCurrent(context(false)));
        verifyNoInteractions(mapper);
    }

    private static PlatformRequestContext context(boolean write) {
        return new PlatformRequestContext(new SubjectId(21), CredentialType.PERSON, AccessChannel.CONSOLE, 3,
                write ? Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.MODEL_PLATFORM_WRITE)
                        : Set.of(PlatformPermissions.SESSION_ACCESS), "synthetic-model-facts");
    }
}

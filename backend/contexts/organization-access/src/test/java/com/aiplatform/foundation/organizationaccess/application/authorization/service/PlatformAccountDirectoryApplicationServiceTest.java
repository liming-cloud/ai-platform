package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountDirectory;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformAccountDirectoryApplicationServiceTest {

    @Test
    void 缺少账户治理权限的平台会话不得读取账户目录() {
        PlatformAccountDirectory directory = query -> { throw new AssertionError("未授权请求不得访问目录"); };
        var service = new PlatformAccountDirectoryApplicationService(directory);
        var context = new PlatformRequestContext(new SubjectId(101L), CredentialType.PERSON,
                AccessChannel.CONSOLE, 1L, Set.of(PlatformPermissions.SESSION_ACCESS), "trc_account_directory");

        assertThrows(IllegalArgumentException.class, () -> service.list(context, new PageQuery(1, 20)));
    }
}

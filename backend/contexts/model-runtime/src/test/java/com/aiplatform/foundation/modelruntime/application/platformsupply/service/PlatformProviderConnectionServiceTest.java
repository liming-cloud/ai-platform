package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.CreatePlatformProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialTransaction;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.net.URI;
import java.time.Clock;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformProviderConnectionServiceTest {

    @Test
    void 没有模型平台权限的会话不得建立平台连接() {
        var service = new PlatformProviderConnectionService(
                new ForbiddenPathRepository(),
                new ForbiddenPathCredentialWriter(),
                java.util.function.Supplier::get,
                Clock.systemUTC(),
                () -> 310000000000000001L);
        try (var apiKey = SecretValue.copyOf("test-api-key".toCharArray())) {
            assertThrows(
                    ModelCatalogPermissionDeniedException.class,
                    () -> service.create(new CreatePlatformProviderConnectionCommand(
                            sessionWithoutPlatformModelWrite(),
                            "platform-connection-0001",
                            ProviderKind.OPENAI_COMPATIBLE,
                            "平台连接",
                            URI.create("https://models.example.com/v1"),
                            "本地研发",
                            AuthenticationType.API_KEY,
                            apiKey)));
        }
    }

    private static PlatformRequestContext sessionWithoutPlatformModelWrite() {
        return new PlatformRequestContext(
                new SubjectId(101L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                Set.of(PlatformPermissions.SESSION_ACCESS),
                "trc_platform_supply");
    }

    private static final class ForbiddenPathRepository
            implements PlatformProviderConnectionRepository {
        @Override
        public com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreationOutcome create(
                com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation creation) {
            throw new AssertionError("无权请求不得触及平台连接仓储");
        }
    }

    private static final class ForbiddenPathCredentialWriter
            implements PlatformProviderConnectionCredentialWriter {
        @Override
        public void write(
                com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference reference,
                SecretValue apiKey) {
            throw new AssertionError("无权请求不得写入平台凭据");
        }
    }
}

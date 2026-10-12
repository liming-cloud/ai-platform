package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.PublishPlatformProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionPublicationRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.*;
import com.aiplatform.foundation.security.identity.*;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformProviderConnectionPublicationServiceTest {
    private final PlatformProviderConnectionRepository connections = mock(PlatformProviderConnectionRepository.class);
    private final PlatformProviderConnectionPublicationRepository publications = mock(PlatformProviderConnectionPublicationRepository.class);
    private final PlatformProviderConnectionPublicationService service = new PlatformProviderConnectionPublicationService(
            connections, publications, () -> 320000000000000001L, Duration.ofHours(24));

    @Test void 无平台模型权限不得发布或读取且不能触及存储() {
        var context = context(Set.of(PlatformPermissions.SESSION_ACCESS));
        assertThrows(ModelCatalogPermissionDeniedException.class,
                () -> service.publish(new PublishPlatformProviderConnectionCommand(context, "310000000000000001", 1L)));
        assertThrows(ModelCatalogPermissionDeniedException.class,
                () -> service.get(context, "310000000000000001", 1L));
        verifyNoInteractions(connections, publications);
    }

    @Test void 当前修订没有发布事实时不能借用历史发布() {
        var id = new PlatformProviderConnectionId(310000000000000001L);
        var connection = PlatformProviderConnection.create(
                id,
                ProviderKind.DETERMINISTIC,
                com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
                "合成连接",
                URI.create("memory://synthetic"),
                "local",
                AuthenticationType.API_KEY,
                new CredentialReference("secret://platform-provider-connection/" + id.value()),
                Instant.parse("2026-10-12T00:00:00Z"))
                .recordSuccessfulProbe("CONNECTION_OK", Instant.parse("2026-10-12T00:00:00Z"));
        when(connections.findById(id)).thenReturn(Optional.of(connection));
        when(publications.find(id, 1L)).thenReturn(Optional.empty());
        var result = service.get(context(Set.of(PlatformPermissions.MODEL_PLATFORM_WRITE)), id.toString(), 1L);
        assertFalse(result.published());
        assertNull(result.publicationRevision());
        assertThrows(ProviderConnectionConcurrentModificationException.class,
                () -> service.get(context(Set.of(PlatformPermissions.MODEL_PLATFORM_WRITE)), id.toString(), 0L));
        verify(publications).find(id, 1L);
        verifyNoMoreInteractions(publications);
    }

    @Test void 必需发布依赖缺失不能装配可写服务() {
        assertThrows(NullPointerException.class, () -> new PlatformProviderConnectionPublicationService(
                connections, null, () -> 1L, Duration.ofHours(24)));
    }

    private static PlatformRequestContext context(Set<PermissionCode> permissions) {
        var admitted = new java.util.HashSet<>(permissions);
        admitted.add(PlatformPermissions.SESSION_ACCESS);
        return new PlatformRequestContext(new SubjectId(101L), CredentialType.PERSON, AccessChannel.CONSOLE,
                1L, admitted, "trc_connection_publication");
    }
}

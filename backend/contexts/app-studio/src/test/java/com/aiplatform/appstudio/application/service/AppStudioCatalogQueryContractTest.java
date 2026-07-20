package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.application.error.AppStudioErrorCodes;
import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.appstudio.domain.port.AiApplicationRepository;
import com.aiplatform.appstudio.domain.port.ApplicationReleaseRepository;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.appstudio.domain.port.chat.ChatCompletionPort;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunQueryPort;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunSnapshotPort;
import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.appstudio.domain.port.retrieval.KnowledgeRetrievalPort;
import com.aiplatform.appstudio.presentation.permission.AppStudioPermissionCatalog;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.port.PermissionCheckPort;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import com.aiplatform.shared.security.port.ResourceAudienceQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppStudioCatalogQueryContractTest {
    private static final TenantId TENANT = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");

    @Test
    void catalogUsesOneBoundedApplicationWindowAndOneBatchActiveReleaseQuery() {
        AiApplicationRepository applications = mock(AiApplicationRepository.class);
        ApplicationReleaseRepository releases = mock(ApplicationReleaseRepository.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        when(applications.findCatalogWindowByTenant(TENANT, 201)).thenReturn(List.of(
                application("3001"), application("3002"), application("3003")
        ));
        when(releases.findActiveByApplicationIds(TENANT, List.of("3001", "3002", "3003")))
                .thenReturn(Map.of());

        assertThat(service(applications, releases, audits).listApplications(TENANT, "1001"))
                .extracting(result -> result.application().applicationId())
                .containsExactly("3001", "3002", "3003");

        verify(applications).findCatalogWindowByTenant(TENANT, 201);
        verify(releases).findActiveByApplicationIds(TENANT, List.of("3001", "3002", "3003"));
        verify(releases, never()).findActive(any(), any());
    }

    @Test
    void twoHundredAndFirstApplicationFailsClosedAndEmitsOperationalAudit() {
        AiApplicationRepository applications = mock(AiApplicationRepository.class);
        ApplicationReleaseRepository releases = mock(ApplicationReleaseRepository.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        List<AiApplication> probe = IntStream.rangeClosed(1, 201)
                .mapToObj(index -> application(String.valueOf(3000 + index)))
                .toList();
        when(applications.findCatalogWindowByTenant(TENANT, 201)).thenReturn(probe);

        assertThatThrownBy(() -> service(applications, releases, audits).listApplications(TENANT, "1001"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(AppStudioErrorCodes.CATALOG_WINDOW_EXCEEDED.code())
                .hasMessageContaining("启用分页目录");

        verify(releases, never()).findActiveByApplicationIds(any(), any());
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audits).publish(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("SECURITY_APP_CATALOG_WINDOW_EXCEEDED");
        assertThat(audit.getValue().detailAttributes())
                .containsEntry("limit", 200)
                .containsEntry("observedAtLeast", 201);
    }

    private static AppStudioApplicationService service(
            AiApplicationRepository applications,
            ApplicationReleaseRepository releases,
            AuditEventPublisher audits
    ) {
        RbacAuthorizationQueryPort authorization = mock(RbacAuthorizationQueryPort.class);
        when(authorization.findPermissionCodes(TENANT, "user", "1001"))
                .thenReturn(Set.of(AppStudioPermissionCatalog.APPLICATION_READ));
        ResourceAudienceQueryPort audience = mock(ResourceAudienceQueryPort.class);
        when(audience.findAllowedUserIds(any(), any(), any(), any())).thenReturn(Set.of());
        return AppStudioApplicationServiceTestFactory.createWithReleases(
                applications,
                mock(ChatSessionRepository.class),
                mock(KnowledgeRetrievalPort.class),
                mock(ModelUsagePolicyPort.class),
                mock(ChatCompletionPort.class),
                mock(AppStudioDebugRunSnapshotPort.class),
                mock(AppStudioDebugRunQueryPort.class),
                mock(PermissionCheckPort.class),
                authorization,
                audience,
                releases,
                audits,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static AiApplication application(String applicationId) {
        return new AiApplication(
                TENANT,
                applicationId,
                AiApplicationType.KNOWLEDGE_QA,
                "应用 " + applicationId,
                "企业知识问答",
                "9001",
                List.of("20"),
                List.of(),
                Set.of("1001"),
                "请基于企业知识回答",
                ApplicationPublicationStatus.DRAFT,
                NOW,
                NOW
        );
    }
}

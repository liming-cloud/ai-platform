package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.application.command.GetDebugEvidenceCommand;
import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.appstudio.domain.Citation;
import com.aiplatform.appstudio.domain.debug.DebugEvidenceView;
import com.aiplatform.appstudio.domain.port.AiApplicationRepository;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.appstudio.domain.port.chat.ChatCompletionPort;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunQueryPort;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunSnapshotPort;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunDetail;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunEvidenceSnapshot;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunQueryFilter;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSummary;
import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.appstudio.domain.port.retrieval.KnowledgeRetrievalPort;
import com.aiplatform.appstudio.presentation.permission.AppStudioPermissionCatalog;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.port.PermissionCheckPort;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AppStudioDebugRunQueryServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-06-16T08:38:00Z");

    @Test
    void listDebugRunsUsesCurrentActorScopeAndReturnsSummaries() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        AppStudioDebugRunSnapshotPort snapshotPort = Mockito.mock(AppStudioDebugRunSnapshotPort.class);
        AppStudioDebugRunQueryPort queryPort = Mockito.mock(AppStudioDebugRunQueryPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(queryPort.listDebugRuns(any())).thenReturn(List.of(new AppStudioDebugRunSummary(
                "23",
                "22",
                "9",
                "3001",
                "appstudio-ui-debug-20260616-hit",
                "3",
                "SUCCEEDED",
                true,
                1,
                2,
                "siliconflow",
                "Qwen/Qwen2.5-7B-Instruct",
                122,
                17,
                2155,
                NOW
        )));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                snapshotPort,
                queryPort,
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var results = service.listDebugRuns(TENANT_ID, "1001", "3001", "9", "trace-1", null, "SUCCEEDED");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().assistantMessageId()).isEqualTo("23");
        assertThat(results.getFirst().traceId()).isEqualTo("appstudio-ui-debug-20260616-hit");
        assertThat(results.getFirst().evidenceCount()).isEqualTo(2);

        ArgumentCaptor<AppStudioDebugRunQueryFilter> filterCaptor = ArgumentCaptor.forClass(AppStudioDebugRunQueryFilter.class);
        verify(queryPort).listDebugRuns(filterCaptor.capture());
        assertThat(filterCaptor.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(filterCaptor.getValue().applicationId()).isEqualTo("3001");
        assertThat(filterCaptor.getValue().userId()).isEqualTo("1001");
        assertThat(filterCaptor.getValue().sessionId()).isEqualTo("9");
        assertThat(filterCaptor.getValue().traceId()).isEqualTo("trace-1");
        assertThat(filterCaptor.getValue().invocationStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void getDebugRunDetailReturnsReplayPayload() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        AppStudioDebugRunSnapshotPort snapshotPort = Mockito.mock(AppStudioDebugRunSnapshotPort.class);
        AppStudioDebugRunQueryPort queryPort = Mockito.mock(AppStudioDebugRunQueryPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(queryPort.findDebugRunDetail(TENANT_ID, "3001", "1001", "23")).thenReturn(Optional.of(new AppStudioDebugRunDetail(
                "23",
                "22",
                "9",
                "3001",
                "appstudio-ui-debug-20260616-hit",
                "3",
                "SUCCEEDED",
                false,
                "用户问句",
                "回答结果",
                "fail-closed 时的解释",
                List.of(new Citation("20", "18", "1", "企业手册", "命中片段", "p1", 0.88)),
                List.of(new AppStudioDebugRunEvidenceSnapshot("20", "18", "1", 0.88, "VECTOR", "kb-20-active-v1")),
                "siliconflow",
                "Qwen/Qwen2.5-7B-Instruct",
                "knowledge-retrieval-fail-closed",
                122,
                17,
                2155,
                "APP_STUDIO_CHAT",
                "AI_APPLICATION",
                "3001",
                null,
                null,
                NOW
        )));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                snapshotPort,
                queryPort,
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.getDebugEvidence(new GetDebugEvidenceCommand(
                TENANT_ID,
                "1001",
                "3001",
                "23",
                DebugEvidenceView.SUMMARY,
                null
        ));

        assertThat(result.masked()).isTrue();
        assertThat(result.detail().assistantMessageId()).isEqualTo("23");
        assertThat(result.detail().citations()).hasSize(1);
        assertThat(result.detail().evidences()).hasSize(1);
        assertThat(result.detail().noAnswerReason()).isEqualTo("fail-closed 时的解释");
    }

    private static AiApplication application() {
        return new AiApplication(
                TENANT_ID,
                "3001",
                AiApplicationType.KNOWLEDGE_QA,
                "知识助手",
                "知识助手描述",
                "9001",
                List.of("20"),
                List.of(),
                java.util.Set.of("1001"),
                "请基于知识库回答",
                ApplicationPublicationStatus.PUBLISHED,
                NOW,
                NOW
        );
    }

    private static PermissionCheckPort allowAllPermissionCheck() {
        PermissionCheckPort permissionCheckPort = Mockito.mock(PermissionCheckPort.class);
        when(permissionCheckPort.canAccess(any(), any(), any(), any(), any())).thenReturn(true);
        return permissionCheckPort;
    }

    private static RbacAuthorizationQueryPort allowAllAuthorizationQuery() {
        RbacAuthorizationQueryPort authorizationQueryPort = Mockito.mock(RbacAuthorizationQueryPort.class);
        when(authorizationQueryPort.findPermissionCodes(any(), any(), any()))
                .thenReturn(java.util.Set.of(AppStudioPermissionCatalog.APPLICATION_USE));
        return authorizationQueryPort;
    }
}

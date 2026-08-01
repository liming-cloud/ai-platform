package com.aiplatform.foundation.queryruntime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.satoken.web.SaTokenTrustedContextArgumentResolver;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.CandidateCollection;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.CitationValidation;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.QueryDiagnosticFact;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.RetrievalAuthorization;
import com.aiplatform.foundation.trustedquery.application.diagnostic.port.out.QueryDiagnosticFactLookup;
import com.aiplatform.foundation.trustedquery.application.diagnostic.service.QueryDiagnosticService;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryModelUsage;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRequesterId;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRun;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.interfaces.web.query.controller.QueryDiagnosticController;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class QueryDiagnosticHttpTest {

    private static final TenantId TENANT = new TenantId(700000000000000001L);
    private static final QueryRunId RUN = new QueryRunId(430000000000000001L);

    @Test
    void 诊断接口应从Bearer令牌恢复审计身份且忽略伪造租户参数() throws Exception {
        var sessions = mock(AuthenticatedSessionGateway.class);
        var runs = mock(QueryRunRepository.class);
        var facts = mock(QueryDiagnosticFactLookup.class);
        var tokenContext = tokenContext(Set.of(new PermissionCode("audit:read")));
        when(sessions.resolve("audit-session-token")).thenReturn(tokenContext);
        when(runs.find(TENANT, RUN)).thenReturn(Optional.of(answered()));
        when(facts.find(TENANT, RUN)).thenReturn(Optional.of(new QueryDiagnosticFact(
                CandidateCollection.COLLECTED,
                4,
                RetrievalAuthorization.AUTHORIZED,
                7,
                3,
                CitationValidation.VALIDATED)));
        var mvc = MockMvcBuilders.standaloneSetup(new QueryDiagnosticController(
                        new QueryDiagnosticService(runs, facts)))
                .setCustomArgumentResolvers(new SaTokenTrustedContextArgumentResolver(sessions, () -> 123L))
                .build();

        mvc.perform(get("/api/v1/query-runs/{queryRunId}/diagnostic", RUN)
                        .header("Authorization", "Bearer audit-session-token")
                        .param("tenantId", "100000000000000099"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryRunId").value(RUN.toString()))
                .andExpect(jsonPath("$.candidateCount").value(4))
                .andExpect(jsonPath("$.citationCount").value(1))
                .andExpect(jsonPath("$.totalTokens").value(15))
                .andExpect(jsonPath("$.candidateContent").doesNotExist())
                .andExpect(jsonPath("$.modelDeploymentId").doesNotExist());

        verify(sessions).resolve("audit-session-token");
        verify(runs).find(TENANT, RUN);
        verify(facts).find(TENANT, RUN);
    }

    private static QueryRun answered() {
        var running = QueryRun.start(
                        TENANT,
                        RUN,
                        420000000000000001L,
                        new QueryRequesterId(730000000000000001L),
                        "a".repeat(64),
                        Instant.parse("2026-08-01T00:00:00Z"))
                .acceptQuota(new com.aiplatform.foundation.trustedquery.domain.query.model.QueryQuotaSnapshot(
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-02T00:00:00Z"),
                        10, 1, 2, Instant.parse("2026-08-01T00:02:00Z")));
        return running.answer(
                "牛奶位于一层。\n\n引用：[1]",
                java.util.List.of(new com.aiplatform.foundation.trustedquery.domain.query.model.QueryCitation(
                        1, 220000000000000001L, 1, "b".repeat(64), 0, "牛奶位于一层冷藏区。", 0.9)),
                new QueryModelUsage(10, 5, 15),
                Instant.parse("2026-08-01T00:00:01Z"));
    }

    private static TrustedRequestContext tokenContext(Set<PermissionCode> permissions) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(720000000000000001L),
                new MembershipId(730000000000000001L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                permissions,
                "trc-diagnostic-session");
    }
}

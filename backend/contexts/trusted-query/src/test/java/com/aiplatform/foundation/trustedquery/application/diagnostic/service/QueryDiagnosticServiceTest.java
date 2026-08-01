package com.aiplatform.foundation.trustedquery.application.diagnostic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.CandidateCollection;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.CitationValidation;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.QueryDiagnosticFact;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.RetrievalAuthorization;
import com.aiplatform.foundation.trustedquery.application.diagnostic.exception.QueryDiagnosticPermissionDeniedException;
import com.aiplatform.foundation.trustedquery.application.run.exception.QueryRunNotFoundException;
import com.aiplatform.foundation.trustedquery.application.diagnostic.port.out.QueryDiagnosticFactLookup;
import com.aiplatform.foundation.trustedquery.application.diagnostic.query.GetQueryDiagnosticQuery;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryModelUsage;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRequesterId;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRun;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryDiagnosticServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final QueryRunId RUN = new QueryRunId(430000000000000001L);

    @Mock
    private QueryRunRepository runs;

    @Mock
    private QueryDiagnosticFactLookup facts;

    @Test
    void 无审计权限时在读取任何事实前拒绝() {
        var service = new QueryDiagnosticService(runs, facts);

        assertThrows(
                QueryDiagnosticPermissionDeniedException.class,
                () -> service.get(new GetQueryDiagnosticQuery(context(Set.of()), RUN.toString())));

        verify(runs, never()).find(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(facts, never()).find(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 返回最小诊断事实且不暴露候选内容() {
        var service = new QueryDiagnosticService(runs, facts);
        var completed = running().answer(
                "牛奶位于一层。\n\n引用：[1]",
                java.util.List.of(new com.aiplatform.foundation.trustedquery.domain.query.model.QueryCitation(
                        1, 220000000000000001L, 1, "a".repeat(64), 0, "牛奶位于一层冷藏区。", 0.9)),
                new QueryModelUsage(10, 5, 15),
                Instant.parse("2026-08-01T00:00:01Z"));
        when(runs.find(TENANT, RUN)).thenReturn(Optional.of(completed));
        when(facts.find(TENANT, RUN)).thenReturn(Optional.of(new QueryDiagnosticFact(
                CandidateCollection.COLLECTED,
                4,
                RetrievalAuthorization.AUTHORIZED,
                7,
                3,
                CitationValidation.VALIDATED)));

        var result = service.get(new GetQueryDiagnosticQuery(
                context(Set.of(new PermissionCode("audit:read"))), RUN.toString()));

        assertEquals(RUN.toString(), result.queryRunId());
        assertEquals("ANSWERED", result.status());
        assertEquals(4, result.candidateCount());
        assertEquals(1, result.citationCount());
        assertEquals(15, result.totalTokens());
    }

    @Test
    void 不存在和跨租户查询都返回同一种不可得结果() {
        var service = new QueryDiagnosticService(runs, facts);
        var otherTenant = new TenantId(100000000000000002L);
        when(runs.find(TENANT, RUN)).thenReturn(Optional.empty());
        when(runs.find(otherTenant, RUN)).thenReturn(Optional.empty());

        assertThrows(
                QueryRunNotFoundException.class,
                () -> service.get(new GetQueryDiagnosticQuery(
                        context(Set.of(new PermissionCode("audit:read"))), RUN.toString())));
        assertThrows(
                QueryRunNotFoundException.class,
                () -> service.get(new GetQueryDiagnosticQuery(
                        context(otherTenant, Set.of(new PermissionCode("audit:read"))), RUN.toString())));

        verify(facts, never()).find(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static QueryRun running() {
        return QueryRun.start(
                        TENANT,
                        RUN,
                        420000000000000001L,
                        new QueryRequesterId(3382738893456539650L),
                        "b".repeat(64),
                        Instant.parse("2026-08-01T00:00:00Z"))
                .acceptQuota(new com.aiplatform.foundation.trustedquery.domain.query.model.QueryQuotaSnapshot(
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-02T00:00:00Z"), 10, 1, 2,
                        Instant.parse("2026-08-01T00:02:00Z")));
    }

    private static TrustedRequestContext context(Set<PermissionCode> permissions) {
        return context(TENANT, permissions);
    }

    private static TrustedRequestContext context(
            TenantId tenantId,
            Set<PermissionCode> permissions) {
        return new TrustedRequestContext(
                tenantId,
                new SubjectId(3282738893456539650L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                permissions,
                "trace-query-diagnostic");
    }
}

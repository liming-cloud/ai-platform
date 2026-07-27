package com.aiplatform.foundation.queryruntime;

import com.aiplatform.foundation.trustedquery.interfaces.web.query.controller.QueryRunController;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.application.run.command.CreateQueryRunCommand;
import com.aiplatform.foundation.trustedquery.application.run.dto.CreateQueryRunResult;
import com.aiplatform.foundation.trustedquery.application.run.query.ListQueryRunEventsQuery;
import com.aiplatform.foundation.trustedquery.application.run.dto.QueryRunEventPage;
import com.aiplatform.foundation.trustedquery.application.run.dto.QueryRunEventView;
import com.aiplatform.foundation.trustedquery.application.run.dto.QueryRunView;
import com.aiplatform.foundation.trustedquery.application.run.service.QueryRunEventService;
import com.aiplatform.foundation.trustedquery.application.run.service.QueryRunService;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryModelUsage;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class QueryRunControllerTest {

    private static final String VERSION = "420000000000000001";
    private static final String RUN = "430000000000000001";

    @Test
    void 创建接口传递可信上下文和幂等键并返回资源位置() {
        var useCase = mock(QueryRunService.class);
        when(useCase.create(any())).thenReturn(
                new CreateQueryRunResult(view(), false));
        var controller = new QueryRunController(
                useCase,
                mock(QueryRunEventService.class));

        var response = controller.create(
                context(),
                "query-key-001",
                new QueryRunController.QueryRunRequest(VERSION, "牛奶在哪里"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/v1/query-runs/" + RUN, response.getHeaders().getLocation().toString());
        assertEquals("false", response.getHeaders().getFirst("Idempotency-Replayed"));
        var command = ArgumentCaptor.forClass(CreateQueryRunCommand.class);
        verify(useCase).create(command.capture());
        assertEquals("query-key-001", command.getValue().idempotencyKey());
        assertEquals(VERSION, command.getValue().assistantVersionId());
    }

    @Test
    void 事件接口使用最后事件编号恢复且不重复返回旧事件() {
        var events = mock(QueryRunEventService.class);
        when(events.list(any())).thenReturn(eventPage());
        var controller = new QueryRunController(
                mock(QueryRunService.class),
                events);

        var emitter = controller.events(context(), RUN, "1", 0);

        assertNotNull(emitter);
        var query = ArgumentCaptor.forClass(ListQueryRunEventsQuery.class);
        verify(events).list(query.capture());
        assertEquals(1, query.getValue().afterSequence());
        assertEquals(RUN, query.getValue().queryRunId());
    }

    private static TrustedRequestContext context() {
        return new TrustedRequestContext(
                new TenantId(7100000000000000005L),
                new SubjectId(7200000000000000006L),
                new MembershipId(7300000000000000007L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(
                        new PermissionCode("assistant:query"),
                        new PermissionCode("knowledge:read")),
                "trace-query-controller");
    }

    private static QueryRunView view() {
        return new QueryRunView(
                RUN,
                VERSION,
                "REFUSED",
                null,
                "INSUFFICIENT_EVIDENCE",
                List.of(),
                QueryModelUsage.UNKNOWN,
                1,
                Instant.parse("2026-07-27T05:00:00Z"),
                Instant.parse("2026-07-27T05:00:01Z"));
    }

    private static QueryRunEventPage eventPage() {
        return new QueryRunEventPage(
                List.of(new QueryRunEventView(
                        2,
                        "QUERY_REFUSED",
                        "REFUSED",
                        null,
                        "INSUFFICIENT_EVIDENCE",
                        List.of(),
                        QueryModelUsage.UNKNOWN,
                        Instant.parse("2026-07-27T05:00:01Z"),
                        true)),
                2,
                true);
    }
}

package com.aiplatform.foundation.queryruntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ModelUsage;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.api.QueryRunEventUseCase;
import com.aiplatform.foundation.trustedquery.api.QueryRunUseCase;
import com.aiplatform.foundation.trustedquery.api.model.CreateQueryRunCommand;
import com.aiplatform.foundation.trustedquery.api.model.CreateQueryRunResult;
import com.aiplatform.foundation.trustedquery.api.model.GetQueryRunQuery;
import com.aiplatform.foundation.trustedquery.api.model.ListQueryRunEventsQuery;
import com.aiplatform.foundation.trustedquery.api.model.QueryRunEventPage;
import com.aiplatform.foundation.trustedquery.api.model.QueryRunEventView;
import com.aiplatform.foundation.trustedquery.api.model.QueryRunView;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class QueryRunControllerTest {

    private static final String VERSION = "420000000000000001";
    private static final String RUN = "430000000000000001";

    @Test
    void 创建接口传递可信上下文和幂等键并返回资源位置() {
        var useCase = new RecordingUseCase();
        var controller = new QueryRunController(
                useCase,
                new RecordingEventUseCase());

        var response = controller.create(
                context(),
                "query-key-001",
                new QueryRunController.QueryRunRequest(VERSION, "牛奶在哪里"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/v1/query-runs/" + RUN, response.getHeaders().getLocation().toString());
        assertEquals("false", response.getHeaders().getFirst("Idempotency-Replayed"));
        assertEquals("query-key-001", useCase.command.idempotencyKey());
        assertEquals(VERSION, useCase.command.assistantVersionId());
    }

    @Test
    void 事件接口使用最后事件编号恢复且不重复返回旧事件() {
        var events = new RecordingEventUseCase();
        var controller = new QueryRunController(new RecordingUseCase(), events);

        var emitter = controller.events(context(), RUN, "1", 0);

        assertNotNull(emitter);
        assertEquals(1, events.query.afterSequence());
        assertEquals(RUN, events.query.queryRunId());
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
                ModelUsage.UNKNOWN,
                1,
                Instant.parse("2026-07-27T05:00:00Z"),
                Instant.parse("2026-07-27T05:00:01Z"));
    }

    private static final class RecordingUseCase implements QueryRunUseCase {

        private CreateQueryRunCommand command;

        @Override
        public CreateQueryRunResult create(CreateQueryRunCommand value) {
            command = value;
            return new CreateQueryRunResult(view(), false);
        }

        @Override
        public QueryRunView get(GetQueryRunQuery query) {
            return view();
        }
    }

    private static final class RecordingEventUseCase
            implements QueryRunEventUseCase {

        private ListQueryRunEventsQuery query;

        @Override
        public QueryRunEventPage list(ListQueryRunEventsQuery value) {
            query = value;
            return new QueryRunEventPage(
                    List.of(new QueryRunEventView(
                            2,
                            "QUERY_REFUSED",
                            "REFUSED",
                            null,
                            "INSUFFICIENT_EVIDENCE",
                            List.of(),
                            ModelUsage.UNKNOWN,
                            Instant.parse("2026-07-27T05:00:01Z"),
                            true)),
                    2,
                    true);
        }
    }
}

package com.aiplatform.foundation.queryruntime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ModelUsage;
import com.aiplatform.foundation.organizationaccess.api.model.AccessChannel;
import com.aiplatform.foundation.organizationaccess.api.model.CredentialType;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.api.QueryRunUseCase;
import com.aiplatform.foundation.trustedquery.api.model.CreateQueryRunCommand;
import com.aiplatform.foundation.trustedquery.api.model.CreateQueryRunResult;
import com.aiplatform.foundation.trustedquery.api.model.GetQueryRunQuery;
import com.aiplatform.foundation.trustedquery.api.model.QueryRunView;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class QueryRunControllerTest {

    private static final UUID VERSION =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RUN =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void 创建接口传递可信上下文和幂等键并返回资源位置() {
        var useCase = new RecordingUseCase();
        var controller = new QueryRunController(useCase);

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

    private static TrustedRequestContext context() {
        return new TrustedRequestContext(
                new TenantId(UUID.randomUUID()),
                new SubjectId(UUID.randomUUID()),
                new MembershipId(UUID.randomUUID()),
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
}

package com.aiplatform.foundation.queryruntime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.assistantdelivery.api.QueryAssistantCatalogUseCase;
import com.aiplatform.foundation.assistantdelivery.api.model.ListQueryAssistantsQuery;
import com.aiplatform.foundation.assistantdelivery.api.model.QueryAssistantPage;
import com.aiplatform.foundation.assistantdelivery.api.model.QueryAssistantView;
import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.organizationaccess.api.model.AccessChannel;
import com.aiplatform.foundation.organizationaccess.api.model.CredentialType;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryAssistantControllerTest {

    private static final String ASSISTANT = "410000000000000001";
    private static final String VERSION = "420000000000000001";

    @Test
    void 目录接口传递可信上下文和游标且只返回查询视图() {
        var useCase = new RecordingCatalog();
        var controller = new QueryAssistantController(useCase);

        var page = controller.list(context(), "cursor-001", 10);

        assertEquals("cursor-001", useCase.query.cursor());
        assertEquals(10, useCase.query.limit());
        assertEquals(ASSISTANT, page.items().getFirst().assistantId());
        assertEquals(VERSION, page.items().getFirst().assistantVersionId());
    }

    private static TrustedRequestContext context() {
        return new TrustedRequestContext(
                new TenantId(300000000000000001L),
                new SubjectId(7200000000000000005L),
                new MembershipId(7300000000000000006L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(
                        new PermissionCode("assistant:query"),
                        new PermissionCode("knowledge:read")),
                "trace-query-assistant-controller");
    }

    private static final class RecordingCatalog
            implements QueryAssistantCatalogUseCase {

        private ListQueryAssistantsQuery query;

        @Override
        public QueryAssistantPage list(ListQueryAssistantsQuery value) {
            query = value;
            return new QueryAssistantPage(
                    List.of(new QueryAssistantView(
                            ASSISTANT,
                            "门店导购助手",
                            VERSION,
                            1,
                            Instant.parse("2026-07-27T06:00:00Z"))),
                    null);
        }
    }
}

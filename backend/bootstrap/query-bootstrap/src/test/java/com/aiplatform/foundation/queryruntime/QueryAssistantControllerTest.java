package com.aiplatform.foundation.queryruntime;

import com.aiplatform.foundation.trustedquery.interfaces.web.query.controller.QueryAssistantController;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.assistantdelivery.application.catalog.facade.QueryAssistantCatalogService;
import com.aiplatform.foundation.assistantdelivery.application.catalog.facade.ListQueryAssistantsQuery;
import com.aiplatform.foundation.assistantdelivery.application.catalog.facade.QueryAssistantView;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class QueryAssistantControllerTest {

    private static final String ASSISTANT = "410000000000000001";
    private static final String VERSION = "420000000000000001";

    @Test
    void 目录接口传递可信上下文和页码且只返回查询视图() {
        var query = new AtomicReference<ListQueryAssistantsQuery>();
        var service = mock(QueryAssistantCatalogService.class);
        when(service.list(any())).thenAnswer(invocation -> {
            query.set(invocation.getArgument(0));
            return PageResult.of(
                    List.of(new QueryAssistantView(
                            ASSISTANT,
                            "门店导购助手",
                            VERSION,
                            1,
                            Instant.parse("2026-07-27T06:00:00Z"))),
                    new PageQuery(2, 10),
                    11);
        });
        var controller = new QueryAssistantController(service);

        var page = controller.list(context(), 2, 10);

        assertEquals(2, query.get().page().pageNumber());
        assertEquals(10, query.get().page().pageSize());
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
}

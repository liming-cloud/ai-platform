package com.aiplatform.foundation.queryruntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.application.feedback.command.RecordQueryFeedbackCommand;
import com.aiplatform.foundation.trustedquery.application.feedback.service.QueryFeedbackService;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.model.QueryFeedbackOutcome;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.model.QueryFeedbackReason;
import com.aiplatform.foundation.trustedquery.interfaces.web.query.controller.QueryFeedbackController;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class QueryFeedbackControllerTest {

    @Test
    void 反馈接口只接收受控判断并传递令牌解析的上下文() {
        var service = mock(QueryFeedbackService.class);
        var controller = new QueryFeedbackController(service);
        var context = new TrustedRequestContext(
                new TenantId(7100000000000000005L),
                new SubjectId(7200000000000000006L),
                new MembershipId(7300000000000000007L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode("assistant:query")),
                "trace-query-feedback-controller");

        var response = controller.record(
                context,
                "430000000000000001",
                new QueryFeedbackController.QueryFeedbackRequest(
                        QueryFeedbackOutcome.NEGATIVE,
                        QueryFeedbackReason.INSUFFICIENT));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        var command = ArgumentCaptor.forClass(RecordQueryFeedbackCommand.class);
        verify(service).record(command.capture());
        assertEquals(context, command.getValue().context());
        assertEquals("430000000000000001", command.getValue().queryRunId());
        assertEquals(QueryFeedbackOutcome.NEGATIVE, command.getValue().outcome());
        assertEquals(QueryFeedbackReason.INSUFFICIENT, command.getValue().reason());
    }
}

package com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.dto.AssistantTestRunResult;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.service.AssistantTestRunService;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRunStatus;
import com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.request.AssistantDraftTestRequest;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssistantTestRunControllerTest {

    @Test
    void delegatesTokenDerivedContextAndCurrentQuestionOnly() {
        var tests = mock(AssistantTestRunService.class);
        var controller = new AssistantTestRunController(tests);
        var expected = new AssistantTestRunResult("91", AssistantTestRunStatus.REFUSED, null, List.of());
        when(tests.run(any())).thenReturn(expected);

        var response = controller.run(context(), "11", new AssistantDraftTestRequest("测试问题"));

        assertEquals(201, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        var command = ArgumentCaptor.forClass(
                com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.command.RunAssistantDraftTestCommand.class);
        verify(tests).run(command.capture());
        assertEquals("11", command.getValue().assistantVersionId());
        assertEquals("测试问题", command.getValue().question());
        assertEquals(context(), command.getValue().context());
    }

    private static TrustedRequestContext context() {
        return new TrustedRequestContext(
                new TenantId(700000000000000001L), new SubjectId(720000000000000001L),
                new MembershipId(730000000000000001L), CredentialType.PERSON,
                AccessChannel.CONSOLE, 1, Set.of(), "trace-assistant-http-001");
    }
}

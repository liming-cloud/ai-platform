package com.aiplatform.appstudio.presentation.request;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.appstudio.application.command.SendChatMessageCommand;
import com.aiplatform.shared.kernel.TenantId;
import org.junit.jupiter.api.Test;

class SendChatMessageTraceIdContractTest {
    private static final String MAX_TRACE_ID = "t".repeat(64);
    private static final String OVERSIZED_TRACE_ID = "t".repeat(65);

    @Test
    void httpRequestAcceptsTraceIdAtPersistenceBoundary() {
        assertThatCode(() -> request(MAX_TRACE_ID)).doesNotThrowAnyException();
    }

    @Test
    void httpRequestRejectsTraceIdBeforeControllerDispatchWhenPersistenceBoundaryIsExceeded() {
        assertThatThrownBy(() -> request(OVERSIZED_TRACE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trace ID")
                .hasMessageContaining("64");
    }

    @Test
    void commandAcceptsTraceIdAtPersistenceBoundary() {
        assertThatCode(() -> command(MAX_TRACE_ID)).doesNotThrowAnyException();
    }

    @Test
    void commandRejectsTraceIdBeforeAnyClaimOrProviderCallWhenPersistenceBoundaryIsExceeded() {
        assertThatThrownBy(() -> command(OVERSIZED_TRACE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trace ID")
                .hasMessageContaining("64");
    }

    private static SendChatMessageRequest request(String traceId) {
        return new SendChatMessageRequest("client-1", "hello", null, traceId);
    }

    private static SendChatMessageCommand command(String traceId) {
        return new SendChatMessageCommand(
                TenantId.of("2001"), "1001", "4001", "client-1", "hello", traceId
        );
    }
}

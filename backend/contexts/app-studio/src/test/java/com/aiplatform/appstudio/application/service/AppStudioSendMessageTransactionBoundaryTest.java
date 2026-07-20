package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.appstudio.application.command.SendChatMessageCommand;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class AppStudioSendMessageTransactionBoundaryTest {

    @Test
    void sendMessageDoesNotHoldOneTransactionAcrossRetrievalAndModelCalls() throws Exception {
        Method sendMessage = AppStudioApplicationService.class.getMethod(
                "sendMessage", SendChatMessageCommand.class
        );

        assertThat(sendMessage.getAnnotation(Transactional.class)).isNull();
    }
}

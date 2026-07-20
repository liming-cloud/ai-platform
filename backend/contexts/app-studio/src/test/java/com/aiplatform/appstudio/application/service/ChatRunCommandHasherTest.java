package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.appstudio.application.command.SendChatMessageCommand;
import com.aiplatform.shared.kernel.TenantId;
import org.junit.jupiter.api.Test;

class ChatRunCommandHasherTest {

    @Test
    void hashCoversCanonicalTenantSessionApplicationActorClientMessageAndContent() {
        SendChatMessageCommand baseline = command("2001", "1001", "4001", "client-1", "hello");
        String baselineHash = ChatRunCommandHasher.hash(baseline, "3001");

        assertThat(baselineHash).hasSize(64);
        assertThat(ChatRunCommandHasher.hash(command("2002", "1001", "4001", "client-1", "hello"), "3001"))
                .isNotEqualTo(baselineHash);
        assertThat(ChatRunCommandHasher.hash(command("2001", "1002", "4001", "client-1", "hello"), "3001"))
                .isNotEqualTo(baselineHash);
        assertThat(ChatRunCommandHasher.hash(command("2001", "1001", "4002", "client-1", "hello"), "3001"))
                .isNotEqualTo(baselineHash);
        assertThat(ChatRunCommandHasher.hash(command("2001", "1001", "4001", "client-2", "hello"), "3001"))
                .isNotEqualTo(baselineHash);
        assertThat(ChatRunCommandHasher.hash(command("2001", "1001", "4001", "client-1", "changed"), "3001"))
                .isNotEqualTo(baselineHash);
        assertThat(ChatRunCommandHasher.hash(baseline, "3002")).isNotEqualTo(baselineHash);
    }

    @Test
    void hashUsesCommandCanonicalizationAndNeverContainsRawContent() {
        SendChatMessageCommand command = command("2001", " 1001 ", " 4001 ", " client-1 ", " secret prompt ");

        String hash = ChatRunCommandHasher.hash(command, " 3001 ");

        assertThat(hash)
                .isEqualTo(ChatRunCommandHasher.hash(
                        command("2001", "1001", "4001", "client-1", "secret prompt"),
                        "3001"))
                .doesNotContain("secret")
                .matches("[0-9a-f]{64}");
    }

    private static SendChatMessageCommand command(
            String tenantId,
            String actorId,
            String sessionId,
            String clientMessageId,
            String content
    ) {
        return new SendChatMessageCommand(
                TenantId.of(tenantId),
                actorId,
                sessionId,
                clientMessageId,
                content,
                "trace-1"
        );
    }
}

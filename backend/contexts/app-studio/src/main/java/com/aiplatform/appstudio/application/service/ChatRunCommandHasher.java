package com.aiplatform.appstudio.application.service;

import com.aiplatform.appstudio.application.command.SendChatMessageCommand;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 为持久化聊天命令生成稳定身份。长度前缀编码避免字段拼接歧义，摘要之外不落原始问题正文。
 */
final class ChatRunCommandHasher {
    private ChatRunCommandHasher() {
    }

    static String hash(SendChatMessageCommand command, String applicationId) {
        Objects.requireNonNull(command, "聊天命令不能为空");
        MessageDigest digest = sha256();
        update(digest, command.tenantId().value());
        update(digest, command.sessionId());
        update(digest, requireText(applicationId, "应用 ID"));
        update(digest, command.actorId());
        update(digest, command.clientMessageId());
        update(digest, command.message());
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = requireText(value, "命令哈希字段").getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

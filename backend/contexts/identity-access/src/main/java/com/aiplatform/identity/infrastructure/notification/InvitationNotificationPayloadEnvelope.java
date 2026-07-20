package com.aiplatform.identity.infrastructure.notification;

/**
 * 邀请通知密文的版本化持久化信封；字段只描述密文元数据，不包含明文激活地址。
 */
record InvitationNotificationPayloadEnvelope(
        String schema,
        String algorithm,
        String keyId,
        String nonce,
        String ciphertext
) {
}

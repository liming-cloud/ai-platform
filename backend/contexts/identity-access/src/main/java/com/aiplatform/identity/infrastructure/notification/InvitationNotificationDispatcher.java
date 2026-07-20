package com.aiplatform.identity.infrastructure.notification;

import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aip.identity.invitation-mail.enabled", havingValue = "true")
public class InvitationNotificationDispatcher {
    private final IamAuthorizationMapper mapper;
    private final JavaMailSender mailSender;
    private final InvitationNotificationPayloadCipher payloadCipher;
    private final String sender;

    public InvitationNotificationDispatcher(
            IamAuthorizationMapper mapper,
            JavaMailSender mailSender,
            InvitationNotificationPayloadCipher payloadCipher,
            @Value("${aip.identity.invitation-mail.from:no-reply@example.com}") String sender
    ) {
        this.mapper = Objects.requireNonNull(mapper, "IAM Mapper 不能为空");
        this.mailSender = Objects.requireNonNull(mailSender, "邮件发送器不能为空");
        this.payloadCipher = Objects.requireNonNull(payloadCipher, "邀请通知密文服务不能为空");
        this.sender = Objects.requireNonNull(sender, "邀请发件人不能为空");
    }

    @Scheduled(fixedDelayString = "${aip.identity.invitation-mail.poll-delay-ms:15000}")
    public void dispatch() {
        for (var notification : mapper.findQueuedInvitationNotifications(20)) {
            if (mapper.claimInvitationNotification(notification.getId()) != 1) continue;
            try {
                String activationUrl = payloadCipher.decrypt(
                        String.valueOf(notification.getTenantId()),
                        notification.getAggregateId(),
                        notification.getPayloadJson()
                );
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(sender);
                message.setTo(notification.getRecipient());
                message.setSubject("AI 平台账号激活邀请");
                message.setText("你已被邀请加入 AI 平台。请在 24 小时内完成账号激活：\n" + activationUrl);
                mailSender.send(message);
                if (mapper.markInvitationNotificationDelivered(notification.getId()) != 1) {
                    throw new IllegalStateException("邀请通知交付状态已变化");
                }
            } catch (Exception exception) {
                mapper.markInvitationNotificationFailed(
                        notification.getId(),
                        "INVITATION_NOTIFICATION_FAILED:" + exception.getClass().getSimpleName()
                );
            }
        }
    }
}

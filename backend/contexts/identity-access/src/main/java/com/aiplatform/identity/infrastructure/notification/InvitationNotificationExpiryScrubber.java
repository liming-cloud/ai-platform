package com.aiplatform.identity.infrastructure.notification;

import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 始终启用的邀请通知密文过期清理器。
 *
 * <p>密文保留周期属于数据生命周期，而不是邮件交付能力；即使邮件发送被关闭，过期邀请的
 * bearer payload 也必须被清除。</p>
 */
@Component
public class InvitationNotificationExpiryScrubber {
    private final IamAuthorizationMapper mapper;

    public InvitationNotificationExpiryScrubber(IamAuthorizationMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "IAM Mapper 不能为空");
    }

    @Scheduled(fixedDelayString = "${aip.identity.invitation-outbox.expiry-poll-delay-ms:15000}")
    public void scrubExpiredPayloads() {
        mapper.expireInvitationNotificationPayloads();
    }
}

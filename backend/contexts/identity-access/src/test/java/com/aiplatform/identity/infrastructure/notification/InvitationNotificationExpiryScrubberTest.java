package com.aiplatform.identity.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class InvitationNotificationExpiryScrubberTest {
    @Test
    void scrubsExpiredPayloadsThroughTheDedicatedMapperOperation() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);

        new InvitationNotificationExpiryScrubber(mapper).scrubExpiredPayloads();

        verify(mapper).expireInvitationNotificationPayloads();
    }

    @Test
    void remainsConfiguredWhenMailDeliveryIsDisabled() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);

        new ApplicationContextRunner()
                .withBean(IamAuthorizationMapper.class, () -> mapper)
                .withPropertyValues("aip.identity.invitation-mail.enabled=false")
                .withUserConfiguration(NotificationComponents.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(InvitationNotificationExpiryScrubber.class);
                    assertThat(context).doesNotHaveBean(InvitationNotificationDispatcher.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({InvitationNotificationExpiryScrubber.class, InvitationNotificationDispatcher.class})
    static class NotificationComponents {
    }
}

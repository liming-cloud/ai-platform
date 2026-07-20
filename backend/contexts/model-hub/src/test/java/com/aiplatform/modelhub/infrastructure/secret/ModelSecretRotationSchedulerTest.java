package com.aiplatform.modelhub.infrastructure.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ModelSecretRotationSchedulerTest {
    @Test
    void logsOnlySafeCountsForSuccessfulBatch() {
        ModelSecretRotationService service = mock(ModelSecretRotationService.class);
        when(service.activeKeyId()).thenReturn("kms-active-2026");
        when(service.rotateBatch()).thenReturn(new ModelSecretRotationBatchResult(3, 2, 1, 0, 7));

        String output = captureLog(() -> new ModelSecretRotationScheduler(service).scheduledRotate());

        assertThat(output)
                .contains("activeKeyId=kms-active-2026")
                .contains("selected=3")
                .contains("rotated=2")
                .contains("conflicted=1")
                .contains("failed=0")
                .contains("backlog=7");
    }

    @Test
    void neverLogsExceptionMessageThatMayContainCredentialMaterial() {
        ModelSecretRotationService service = mock(ModelSecretRotationService.class);
        when(service.activeKeyId()).thenReturn("kms-active-2026");
        when(service.rotateBatch()).thenThrow(new IllegalStateException("sk-live-must-never-appear"));

        String output = captureLog(() -> new ModelSecretRotationScheduler(service).scheduledRotate());

        assertThat(output)
                .contains("failureType=IllegalStateException")
                .doesNotContain("sk-live-must-never-appear");
    }

    private static String captureLog(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(ModelSecretRotationScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
            return appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}

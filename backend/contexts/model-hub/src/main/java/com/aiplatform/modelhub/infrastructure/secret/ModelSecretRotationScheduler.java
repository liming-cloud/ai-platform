package com.aiplatform.modelhub.infrastructure.secret;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时驱动密钥轮换；日志只包含 key ID、失败类型和安全计数。 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "aip.model-hub.secret.rotation.enabled", havingValue = "true")
public class ModelSecretRotationScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(ModelSecretRotationScheduler.class);

    private final ModelSecretRotationService rotationService;

    public ModelSecretRotationScheduler(ModelSecretRotationService rotationService) {
        this.rotationService = rotationService;
    }

    @Scheduled(fixedDelayString = "${aip.model-hub.secret.rotation.poll-delay-ms:5000}")
    public void scheduledRotate() {
        try {
            ModelSecretRotationBatchResult result = rotationService.rotateBatch();
            if (result.selected() == 0 && result.backlog() == 0) {
                LOG.debug("model-secret-rotation activeKeyId={} backlog=0", rotationService.activeKeyId());
            } else {
                LOG.info(
                        "model-secret-rotation activeKeyId={} selected={} rotated={} conflicted={} failed={} backlog={}",
                        rotationService.activeKeyId(),
                        result.selected(),
                        result.rotated(),
                        result.conflicted(),
                        result.failed(),
                        result.backlog()
                );
            }
        } catch (RuntimeException exception) {
            LOG.error(
                    "model-secret-rotation activeKeyId={} failureType={}",
                    rotationService.activeKeyId(),
                    sanitizedFailureType(exception)
            );
        }
    }

    private static String sanitizedFailureType(RuntimeException exception) {
        String simpleName = exception.getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank() ? "RotationFailure" : simpleName;
    }
}

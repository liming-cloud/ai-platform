package com.aiplatform.appstudio.infrastructure.recovery;

import com.aiplatform.appstudio.application.result.ChatRunRecoveryBatchResult;
import com.aiplatform.appstudio.application.service.ChatRunRecoveryService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时恢复 RESULT_RECEIVED 运行；日志只记录安全计数或异常类型，绝不包含 finalization payload。
 */
@Component
@EnableScheduling
public class ChatRunRecoveryScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(ChatRunRecoveryScheduler.class);

    private final ChatRunRecoveryService recoveryService;
    private final int batchSize;

    public ChatRunRecoveryScheduler(
            ChatRunRecoveryService recoveryService,
            @Value("${aip.app-studio.chat-run-recovery.batch-size:20}") int batchSize
    ) {
        this.recoveryService = Objects.requireNonNull(recoveryService, "聊天运行恢复服务不能为空");
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("聊天运行恢复 batch size 必须位于 1..100");
        }
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${aip.app-studio.chat-run-recovery.fixed-delay-ms:5000}")
    public void scheduledRecover() {
        try {
            ChatRunRecoveryBatchResult result = recoveryService.recoverBatch(batchSize);
            if (result.claimed() > 0) {
                LOG.info(
                        "chat run recovery batch finished: claimed={}, completed={}, failed={}",
                        result.claimed(), result.completed(), result.failed()
                );
            }
        } catch (RuntimeException exception) {
            LOG.warn("chat run recovery batch failed: failureType={}", exception.getClass().getSimpleName());
        }
    }
}

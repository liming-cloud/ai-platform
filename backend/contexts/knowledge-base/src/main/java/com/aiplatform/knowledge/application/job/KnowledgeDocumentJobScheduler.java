package com.aiplatform.knowledge.application.job;

import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 重启恢复与 MQ 丢失时的数据库轮询基线。
 */
@Component
@ConditionalOnProperty(
        name = "aip.knowledge.job-worker.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class KnowledgeDocumentJobScheduler {
    private final KnowledgeDocumentJobWorker worker;

    public KnowledgeDocumentJobScheduler(KnowledgeDocumentJobWorker worker) {
        this.worker = Objects.requireNonNull(worker, "文档作业 worker 不能为空");
    }

    @Scheduled(
            initialDelayString = "${aip.knowledge.job-worker.initial-delay-ms:1000}",
            fixedDelayString = "${aip.knowledge.job-worker.poll-delay-ms:1000}"
    )
    public void recoverDueJobs() {
        worker.drainAvailable();
    }
}

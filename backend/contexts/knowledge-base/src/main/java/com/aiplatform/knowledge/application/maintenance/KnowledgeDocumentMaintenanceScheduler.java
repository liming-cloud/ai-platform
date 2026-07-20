package com.aiplatform.knowledge.application.maintenance;

import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 周期唤醒 durable 对象与索引维护；数据库 claim 是唯一任务事实。 */
@Component
@ConditionalOnProperty(
        name = "aip.knowledge.maintenance.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class KnowledgeDocumentMaintenanceScheduler {
    private final KnowledgeDocumentMaintenanceWorker worker;

    public KnowledgeDocumentMaintenanceScheduler(KnowledgeDocumentMaintenanceWorker worker) {
        this.worker = Objects.requireNonNull(worker, "知识维护 worker 不能为空");
    }

    @Scheduled(
            initialDelayString = "${aip.knowledge.maintenance.initial-delay-ms:60000}",
            fixedDelayString = "${aip.knowledge.maintenance.poll-delay-ms:60000}"
    )
    public void recoverMaintenance() {
        worker.drainAvailable();
    }
}

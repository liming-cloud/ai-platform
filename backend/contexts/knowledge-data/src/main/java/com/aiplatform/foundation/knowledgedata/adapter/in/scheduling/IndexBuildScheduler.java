package com.aiplatform.foundation.knowledgedata.adapter.in.scheduling;

import com.aiplatform.foundation.knowledgedata.application.indexversion.DefaultIndexBuildWorker;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 以单任务短轮询方式驱动可横向扩展的数据库构建队列。
 */
@Component
@ConditionalOnProperty(
        name = "foundation.ingestion.enabled",
        havingValue = "true")
public final class IndexBuildScheduler {

    private final DefaultIndexBuildWorker worker;

    public IndexBuildScheduler(DefaultIndexBuildWorker worker) {
        this.worker = Objects.requireNonNull(worker, "索引构建工作进程不能为空");
    }

    @Scheduled(
            fixedDelayString = "${foundation.ingestion.poll-delay:PT1S}",
            initialDelayString = "${foundation.ingestion.initial-delay:PT1S}")
    public void processNext() {
        worker.processNext();
    }
}

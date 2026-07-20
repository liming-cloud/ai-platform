package com.aiplatform.knowledge.application.job;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Durable 文档作业轮询、租约和有限重试配置。
 */
@ConfigurationProperties("aip.knowledge.job-worker")
public class KnowledgeDocumentJobProperties {
    private boolean enabled = true;
    private int batchSize = 8;
    private int maxAttempts = 5;
    private Duration leaseDuration = Duration.ofMinutes(15);
    private Duration initialBackoff = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("作业批次大小必须在 1 到 100 之间");
        }
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("最大尝试次数必须在 1 到 5 之间");
        }
        this.maxAttempts = maxAttempts;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = requirePositive(leaseDuration, "任务租约时长");
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {
        this.initialBackoff = requirePositive(initialBackoff, "初始重试退避");
    }

    private static Duration requirePositive(Duration value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为空");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
        return value;
    }
}

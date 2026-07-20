package com.aiplatform.knowledge.application.maintenance;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("aip.knowledge.maintenance")
public class KnowledgeMaintenanceProperties {
    private boolean enabled = true;
    private int batchSize = 50;
    private int maxAttempts = 5;
    private Duration leaseDuration = Duration.ofMinutes(5);
    private Duration initialBackoff = Duration.ofMinutes(1);
    private Duration orphanGracePeriod = Duration.ofHours(1);
    private Duration obsoleteIndexRetention = Duration.ofHours(24);

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
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("维护批次大小必须在 1 到 500 之间");
        }
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("维护最大尝试次数必须在 1 到 5 之间");
        }
        this.maxAttempts = maxAttempts;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = requirePositive(leaseDuration, "维护租约时长");
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {
        this.initialBackoff = requirePositive(initialBackoff, "维护初始退避");
    }

    public Duration getOrphanGracePeriod() {
        return orphanGracePeriod;
    }

    public void setOrphanGracePeriod(Duration orphanGracePeriod) {
        this.orphanGracePeriod = requirePositive(orphanGracePeriod, "孤儿对象观察宽限期");
    }

    public Duration getObsoleteIndexRetention() {
        return obsoleteIndexRetention;
    }

    public void setObsoleteIndexRetention(Duration obsoleteIndexRetention) {
        this.obsoleteIndexRetention = requirePositive(obsoleteIndexRetention, "旧索引保留期");
    }

    private static Duration requirePositive(Duration value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为空");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
        return value;
    }
}

package com.aiplatform.foundation.modelruntime.application.platformsupply.dto.directory;

/** 全目录当前连接修订的独立发布计数；不声明厂商健康或模型运行能力。 */
public record PlatformProviderConnectionDirectorySummary(long publishedConnections) {
    /** @throws IllegalArgumentException 发布计数为负数，不能以非法读取事实生成页面摘要。 */
    public PlatformProviderConnectionDirectorySummary {
        if (publishedConnections < 0) throw new IllegalArgumentException("已发布连接数不能为负数");
    }
}

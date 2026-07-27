package com.aiplatform.foundation.knowledgedata.infrastructure.configuration;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识数据上下文经过类型校验的运行参数。
 */
@ConfigurationProperties("foundation.knowledge-data")
public class KnowledgeDataProperties {

    private int knowledgeSpaceLimit = 3;
    private int dataSourceLimit = 20;
    private Path stagingDirectory = Path.of("/tmp/foundation-text-staging");
    private long maximumTextBytes = TextContentReference.MAX_BYTE_SIZE;
    private Duration indexBuildLeaseDuration = Duration.ofMinutes(1);

    public int getKnowledgeSpaceLimit() {
        return knowledgeSpaceLimit;
    }

    public void setKnowledgeSpaceLimit(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("知识空间配额必须大于零");
        }
        knowledgeSpaceLimit = value;
    }

    public int getDataSourceLimit() {
        return dataSourceLimit;
    }

    public void setDataSourceLimit(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("数据源配额必须大于零");
        }
        dataSourceLimit = value;
    }

    public Path getStagingDirectory() {
        return stagingDirectory;
    }

    public void setStagingDirectory(Path value) {
        if (value == null) {
            throw new IllegalArgumentException("文本暂存目录不能为空");
        }
        stagingDirectory = value;
    }

    public long getMaximumTextBytes() {
        return maximumTextBytes;
    }

    public void setMaximumTextBytes(long value) {
        if (value < 1 || value > TextContentReference.MAX_BYTE_SIZE) {
            throw new IllegalArgumentException("文本暂存上限超出平台安全范围");
        }
        maximumTextBytes = value;
    }

    public Duration getIndexBuildLeaseDuration() {
        return indexBuildLeaseDuration;
    }

    public void setIndexBuildLeaseDuration(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("索引构建租约必须是正时长");
        }
        indexBuildLeaseDuration = value;
    }
}

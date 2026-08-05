package com.aiplatform.foundation.evaluationoperations.infrastructure.configuration;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 私有评测内容在进入对象存储前的本地暂存安全边界。
 */
@ConfigurationProperties("foundation.evaluation-operations.registration")
public class EvaluationOperationsRegistrationProperties {

    private Path stagingDirectory = Path.of("/tmp/foundation-evaluation-staging");
    private long maximumContentBytes = 10L * 1024L * 1024L;

    public Path getStagingDirectory() {
        return stagingDirectory;
    }

    public void setStagingDirectory(Path value) {
        if (value == null) {
            throw new IllegalArgumentException("评测内容暂存目录不能为空");
        }
        stagingDirectory = value;
    }

    public long getMaximumContentBytes() {
        return maximumContentBytes;
    }

    public void setMaximumContentBytes(long value) {
        if (value < 1L) {
            throw new IllegalArgumentException("评测内容暂存上限必须大于零");
        }
        maximumContentBytes = value;
    }
}

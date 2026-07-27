package com.aiplatform.foundation.knowledgedata.application.port.out.index;

import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersion;
import java.util.List;
import java.util.Objects;

/**
 * 构建工作进程独占的限时索引作业租约。
 */
public record IndexBuildLease(
        long leaseId,
        IndexVersion indexVersion,
        List<IndexSourceSnapshot> sources,
        int attempt) {

    public IndexBuildLease {
        if (leaseId <= 0) {
            throw new IllegalArgumentException("构建租约标识必须是正整数");
        }
        Objects.requireNonNull(indexVersion, "索引版本不能为空");
        Objects.requireNonNull(sources, "来源快照不能为空");
        sources = List.copyOf(sources);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("索引构建至少需要一个来源快照");
        }
        if (attempt < 1) {
            throw new IllegalArgumentException("构建尝试次数必须大于零");
        }
    }
}

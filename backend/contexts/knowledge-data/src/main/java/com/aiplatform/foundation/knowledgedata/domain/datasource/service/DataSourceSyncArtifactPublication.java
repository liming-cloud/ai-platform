package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.PublishedSyncArtifact;
import java.util.Objects;

/** 已由领域服务验证的受治理数据源同步产物发布结果。 */
public interface DataSourceSyncArtifactPublication {
    long expectedSourceVersion();
    GovernedDataSource publishedSource();
    PublishedSyncArtifact publication();

    /** 创建已经验证来源版本与产物归属的一次不可变发布结果。 */
    static DataSourceSyncArtifactPublication of(
            long expectedSourceVersion,
            GovernedDataSource publishedSource,
            PublishedSyncArtifact publication) {
        return new Result(expectedSourceVersion, publishedSource, publication);
    }

    /** 通用发布结果实现，避免各来源重复承载相同状态。 */
    record Result(
            long expectedSourceVersion,
            GovernedDataSource publishedSource,
            PublishedSyncArtifact publication) implements DataSourceSyncArtifactPublication {

        public Result {
            if (expectedSourceVersion < 0) {
                throw new IllegalArgumentException("预期来源修订不能为负数");
            }
            publishedSource = Objects.requireNonNull(publishedSource, "已发布数据源不能为空");
            publication = Objects.requireNonNull(publication, "发布事实不能为空");
            if (publishedSource.version() != expectedSourceVersion + 1
                    || !publishedSource.id().equals(publication.artifact().dataSourceId())) {
                throw new IllegalArgumentException("同步产物发布结果不一致");
            }
        }
    }
}

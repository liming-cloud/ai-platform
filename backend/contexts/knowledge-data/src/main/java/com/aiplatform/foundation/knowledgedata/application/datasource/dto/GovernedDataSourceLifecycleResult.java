package com.aiplatform.foundation.knowledgedata.application.datasource.dto;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceOperation;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityStatus;

/**
 * 生命周期动作完成后的最小来源状态投影。
 *
 * <p>该投影只携带控制台决定下一步操作所需的状态、失败类别、同步产物标识和质量结论；
 * 不得加入对象位置、正文、连接参数或质量问题详情。</p>
 */
public record GovernedDataSourceLifecycleResult(
        String dataSourceId,
        String knowledgeSpaceId,
        GovernedDataSourceStatus status,
        long version,
        DataSourceOperation failedOperation,
        String syncArtifactId,
        QualityStatus qualityStatus) {

    public GovernedDataSourceLifecycleResult(
            String dataSourceId,
            String knowledgeSpaceId,
            GovernedDataSourceStatus status,
            long version) {
        this(dataSourceId, knowledgeSpaceId, status, version, null, null, null);
    }

    /** 返回未产生同步产物的来源状态。 */
    public static GovernedDataSourceLifecycleResult from(GovernedDataSource source) {
        return new GovernedDataSourceLifecycleResult(
                source.id().toString(),
                source.knowledgeSpaceId().toString(),
                source.status(),
                source.version(),
                source.failedOperation(),
                null,
                null);
    }

    /** 返回已生成同步产物并完成质量检查的来源状态。 */
    public static GovernedDataSourceLifecycleResult from(
            GovernedDataSource source, SyncArtifact artifact, QualityReport report) {
        return new GovernedDataSourceLifecycleResult(
                source.id().toString(),
                source.knowledgeSpaceId().toString(),
                source.status(),
                source.version(),
                source.failedOperation(),
                artifact.id().toString(),
                report.status());
    }
}

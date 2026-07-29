package com.aiplatform.foundation.knowledgedata.application.datasource.dto;

import java.time.Instant;

/**
 * FILE 来源列表的非敏感读取投影。
 *
 * <p>仅用于让知识管理员恢复来源任务和识别当前可重建的已发布产物；不得加入对象位置、正文、摘要、
 * 质量问题明细或任何凭据。</p>
 */
public record GovernedFileDataSourceSummary(
        String dataSourceId,
        String knowledgeSpaceId,
        String displayName,
        String status,
        long version,
        String failedOperation,
        String publishedArtifactId,
        String publishedQualityStatus,
        Instant publishedAt) {
}

package com.aiplatform.foundation.knowledgedata.application.port.out.index;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import java.util.Objects;

/**
 * 索引版本冻结的单个已发布内容快照。
 */
public record IndexSourceSnapshot(
        IndexSourceKind kind,
        DataSourceId dataSourceId,
        long sourceRevision,
        Long syncArtifactId,
        String qualityStatus,
        String qualityRuleSetVersion,
        TextContentReference content) {

    public IndexSourceSnapshot {
        Objects.requireNonNull(kind, "来源类型不能为空");
        Objects.requireNonNull(dataSourceId, "数据源标识不能为空");
        Objects.requireNonNull(content, "内容引用不能为空");
        if (sourceRevision < 1) {
            throw new IllegalArgumentException("来源修订必须为正数");
        }
        if (kind == IndexSourceKind.LEGACY_TEXT_CONTENT
                && (syncArtifactId != null || qualityStatus != null || qualityRuleSetVersion != null)) {
            throw new IllegalArgumentException("历史文本快照不能携带同步产物质量事实");
        }
        if (kind == IndexSourceKind.GOVERNED_SYNC_ARTIFACT
                && (syncArtifactId == null || qualityStatus == null || qualityRuleSetVersion == null)) {
            throw new IllegalArgumentException("受治理同步产物快照必须冻结产物与质量事实");
        }
    }

    /** 兼容历史文本来源的调用方。 */
    public IndexSourceSnapshot(DataSourceId dataSourceId, TextContentReference content) {
        this(IndexSourceKind.LEGACY_TEXT_CONTENT, dataSourceId, content.version(), null, null, null, content);
    }
}

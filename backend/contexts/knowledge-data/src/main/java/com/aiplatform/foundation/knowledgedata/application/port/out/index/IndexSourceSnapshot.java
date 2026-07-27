package com.aiplatform.foundation.knowledgedata.application.port.out.index;

import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import java.util.Objects;

/**
 * 索引版本冻结的单个已发布内容快照。
 */
public record IndexSourceSnapshot(
        DataSourceId dataSourceId,
        TextContentReference content) {

    public IndexSourceSnapshot {
        Objects.requireNonNull(dataSourceId, "数据源标识不能为空");
        Objects.requireNonNull(content, "内容引用不能为空");
    }
}

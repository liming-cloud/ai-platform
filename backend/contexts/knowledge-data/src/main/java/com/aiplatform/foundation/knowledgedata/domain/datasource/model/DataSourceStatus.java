package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 文本数据源的发布生命周期。
 */
public enum DataSourceStatus implements ValueObject {
    DRAFT,
    CONTENT_READY,
    PUBLISHED
}

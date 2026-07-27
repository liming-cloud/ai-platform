package com.aiplatform.foundation.knowledgedata.domain.indexversion.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 不可逆的索引版本生命周期状态。
 */
public enum IndexVersionStatus implements ValueObject {
    BUILDING,
    READY,
    ACTIVE,
    SUPERSEDED,
    FAILED
}

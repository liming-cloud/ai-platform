package com.aiplatform.foundation.knowledgedata.domain.indexversion;

/**
 * 不可逆的索引版本生命周期状态。
 */
public enum IndexVersionStatus {
    BUILDING,
    READY,
    ACTIVE,
    SUPERSEDED,
    FAILED
}

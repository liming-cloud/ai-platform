package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

/**
 * 可恢复数据源运行操作的稳定标识。
 *
 * <p>该值只用于决定失败后的重试状态，不承载外部错误详情、连接地址或认证材料。</p>
 */
public enum DataSourceOperation {
    PROBE,
    SYNCHRONIZATION
}

package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;

/**
 * 已由领域服务验证的受治理数据源同步完成结果。
 *
 * <p>仓储只能原子提交此结果，不得补写来源归属、质量结论或状态机判断。</p>
 */
public interface DataSourceSynchronizationCompletion {

    /**
     * 返回提交时必须匹配的数据源版本。
     *
     * @return 数据源乐观锁版本
     */
    long expectedSourceVersion();

    /**
     * 返回已完成同步的数据源聚合。
     *
     * @return 已完成同步的数据源
     */
    GovernedDataSource completedSource();

    /**
     * 返回本次同步生成的不可变产物。
     *
     * @return 同步产物
     */
    SyncArtifact artifact();

    /**
     * 返回本次同步生成的质量报告。
     *
     * @return 质量报告
     */
    QualityReport qualityReport();
}

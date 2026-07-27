/**
 * 索引版本聚合、分块策略及仓储契约。
 *
 * <p>允许依赖知识空间标识、领域公共值对象与共享内核；禁止依赖模型厂商、向量库和任务框架。
 * 单个索引版本冻结嵌入修订和来源快照，激活切换由仓储事务保证。</p>
 */
package com.aiplatform.foundation.knowledgedata.domain.indexversion;

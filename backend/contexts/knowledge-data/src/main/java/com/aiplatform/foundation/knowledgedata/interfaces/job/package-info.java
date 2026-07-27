/**
 * 知识数据后台任务入口。
 *
 * <p>只允许依赖具体应用工作服务；禁止直接操作 Mapper、事务或向量库。
 * 调度线程不形成新的领域事务，事务边界由被调用端口实现。</p>
 */
package com.aiplatform.foundation.knowledgedata.interfaces.job;

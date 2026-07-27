/**
 * 知识数据上下文的领域核心。
 *
 * <p>只允许依赖 JDK 与共享内核；禁止依赖应用层、接口层、基础设施及任何技术框架。
 * 聚合根是事务一致性边界，领域包不直接公开给其他限界上下文。</p>
 */
package com.aiplatform.foundation.knowledgedata.domain;

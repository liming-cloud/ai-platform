/**
 * 保存模型目录上下文的聚合、值对象、领域规则和仓储契约。
 *
 * <p>只允许依赖 JDK 与共享内核；禁止依赖 shared-contract、Spring、MyBatis-Plus、
 * Sa-Token、HTTP 和其他上下文。聚合根是事务一致性边界。</p>
 */
package com.aiplatform.foundation.modelruntime.domain;

/**
 * 向其他限界上下文公开知识空间的稳定只读事实。
 *
 * <p>允许依赖应用层 DTO、领域标识和共享内核；禁止公开仓储、MyBatis 或 Web 类型。
 * 本包是跨上下文公开边界，不承担事务和权限校验。</p>
 */
@org.springframework.modulith.NamedInterface("知识空间门面")
package com.aiplatform.foundation.knowledgedata.application.knowledge.facade;

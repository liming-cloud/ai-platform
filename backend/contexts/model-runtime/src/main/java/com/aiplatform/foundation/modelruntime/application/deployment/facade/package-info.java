/**
 * 向其他上下文公开已发布模型部署的只读查询门面。
 *
 * <p>调用方只能依赖稳定快照，不得取得聚合、仓储或 Spring AI 类型。</p>
 */
@org.springframework.modulith.NamedInterface("模型部署门面")
package com.aiplatform.foundation.modelruntime.application.deployment.facade;

/**
 * 查询运行能力；负责可信查询流水线、幂等执行、引用和事件读取。
 *
 * <p>允许依赖查询领域模型及已声明外部门面，禁止直接访问持久化框架。
 * 单次查询运行是应用事务与审计边界。</p>
 */
package com.aiplatform.foundation.trustedquery.application.run;

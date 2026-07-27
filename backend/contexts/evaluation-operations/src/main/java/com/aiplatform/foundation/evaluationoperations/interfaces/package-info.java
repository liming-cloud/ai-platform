/**
 * 评测运维入口层；将 HTTP 请求转换为具体应用服务查询。
 *
 * <p>允许依赖应用层，禁止包含权限规则、领域计算和持久化逻辑。
 * 可信请求上下文构成租户与主体安全边界。</p>
 */
package com.aiplatform.foundation.evaluationoperations.interfaces;

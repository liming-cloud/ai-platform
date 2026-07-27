/**
 * 知识数据上下文的外部入口层。
 *
 * <p>允许依赖具体应用服务和公共传输契约；禁止访问仓储及基础设施实现。
 * Web 入口以可信请求上下文为安全边界，任务入口只负责触发用例。</p>
 */
package com.aiplatform.foundation.knowledgedata.interfaces;

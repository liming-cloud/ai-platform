/**
 * 把模型治理 HTTP 契约转换为应用命令和查询。
 *
 * <p>允许依赖应用服务与传输模型；禁止编排业务事务和返回框架内部类型。
 * 所有入口受服务端权限校验，异常由平台 Web 能力统一映射。</p>
 */
package com.aiplatform.foundation.modelruntime.interfaces.web;

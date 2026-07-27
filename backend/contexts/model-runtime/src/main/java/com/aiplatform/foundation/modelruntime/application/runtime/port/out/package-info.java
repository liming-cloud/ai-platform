/**
 * 公开厂商扩展必须实现的模型工厂端口。
 *
 * <p>工厂只接收平台运行时规格，不得反向取得 Controller、应用服务或仓储。</p>
 */
@org.springframework.modulith.NamedInterface("模型工厂扩展端口")
package com.aiplatform.foundation.modelruntime.application.runtime.port.out;

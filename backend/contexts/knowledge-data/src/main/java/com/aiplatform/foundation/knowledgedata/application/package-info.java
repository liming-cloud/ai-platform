/**
 * 知识数据上下文的用例编排层。
 *
 * <p>允许依赖领域层、共享契约、可信请求上下文及声明的输出端口；禁止依赖 Controller、
 * MyBatis、对象存储客户端和厂商实现。每个公开方法形成权限校验后的用例边界。</p>
 */
package com.aiplatform.foundation.knowledgedata.application;

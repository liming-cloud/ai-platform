package com.aiplatform.foundation.shared.kernel.domain;

/**
 * 标记可由应用服务注入的无状态领域规则。
 *
 * <p>领域策略不承载实体状态，只封装跨用例复用的业务约束；它与命令、结果中的普通值对象不同，
 * 因此允许作为应用服务的长期依赖。</p>
 */
public interface DomainPolicy extends DomainObject {
}

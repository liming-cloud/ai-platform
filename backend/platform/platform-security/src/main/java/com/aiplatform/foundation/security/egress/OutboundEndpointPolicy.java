package com.aiplatform.foundation.security.egress;

import java.net.URI;

/**
 * 在创建网络客户端前验证目标地址的出站安全策略。
 */
@FunctionalInterface
public interface OutboundEndpointPolicy {

    void verify(URI endpoint);
}

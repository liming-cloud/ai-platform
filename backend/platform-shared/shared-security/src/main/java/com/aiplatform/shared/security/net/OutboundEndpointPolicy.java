package com.aiplatform.shared.security.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

/**
 * 企业出站模型地址策略。保存前校验 URI，建连时重新解析并约束实际 socket 目标。
 */
public interface OutboundEndpointPolicy {
    URI validateAndNormalize(String endpoint);

    List<InetAddress> resolveForConnection(String hostname) throws UnknownHostException;
}

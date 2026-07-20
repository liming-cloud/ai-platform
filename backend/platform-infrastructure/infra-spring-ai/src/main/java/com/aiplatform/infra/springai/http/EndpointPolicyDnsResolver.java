package com.aiplatform.infra.springai.http;

import com.aiplatform.shared.security.net.OutboundEndpointPolicy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import org.apache.hc.client5.http.DnsResolver;

/** Apache HTTP 建连前按统一出站策略重新解析并校验目标地址。 */
final class EndpointPolicyDnsResolver implements DnsResolver {
    private final OutboundEndpointPolicy endpointPolicy;

    EndpointPolicyDnsResolver(OutboundEndpointPolicy endpointPolicy) {
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "模型出站地址策略不能为空");
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        List<InetAddress> addresses = endpointPolicy.resolveForConnection(host);
        return addresses.toArray(InetAddress[]::new);
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        endpointPolicy.resolveForConnection(host);
        return host;
    }
}

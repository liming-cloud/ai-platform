package com.aiplatform.foundation.security.egress;

import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对运维显式批准的完整端点放行，其余地址继续执行默认出站策略。
 */
public final class AllowlistedOutboundEndpointPolicy implements OutboundEndpointPolicy {

    private final Set<URI> allowedEndpoints;
    private final OutboundEndpointPolicy fallback;

    public AllowlistedOutboundEndpointPolicy(
            Set<URI> allowedEndpoints,
            OutboundEndpointPolicy fallback) {
        Objects.requireNonNull(allowedEndpoints, "允许的出站端点不能为空");
        this.allowedEndpoints = allowedEndpoints.stream()
                .map(endpoint -> Objects.requireNonNull(
                        endpoint,
                        "允许的出站端点不能包含空值"))
                .map(URI::normalize)
                .collect(Collectors.toUnmodifiableSet());
        this.fallback = Objects.requireNonNull(fallback, "默认出站策略不能为空");
    }

    @Override
    public void verify(URI endpoint) {
        Objects.requireNonNull(endpoint, "供应商地址不能为空");
        if (allowedEndpoints.contains(endpoint.normalize())) {
            return;
        }
        fallback.verify(endpoint);
    }
}

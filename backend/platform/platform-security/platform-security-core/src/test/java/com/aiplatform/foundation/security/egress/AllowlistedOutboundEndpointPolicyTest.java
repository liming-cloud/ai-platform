package com.aiplatform.foundation.security.egress;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AllowlistedOutboundEndpointPolicyTest {

    @Test
    void 只绕过精确匹配且经运维批准的端点() {
        var allowed = URI.create("http://ollama.internal:11434");
        var policy = new AllowlistedOutboundEndpointPolicy(
                Set.of(allowed),
                endpoint -> {
                    throw new UnsafeOutboundEndpointException("默认拒绝");
                });

        assertDoesNotThrow(() -> policy.verify(allowed));
        assertThrows(
                UnsafeOutboundEndpointException.class,
                () -> policy.verify(URI.create("http://ollama.internal:11435")));
    }
}

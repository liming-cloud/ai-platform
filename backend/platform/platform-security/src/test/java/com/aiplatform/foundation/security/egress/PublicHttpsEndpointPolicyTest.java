package com.aiplatform.foundation.security.egress;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PublicHttpsEndpointPolicyTest {

    private final PublicHttpsEndpointPolicy policy = new PublicHttpsEndpointPolicy();

    @ParameterizedTest
    @ValueSource(strings = {
        "http://8.8.8.8/v1",
        "https://127.0.0.1/v1",
        "https://169.254.169.254/latest/meta-data",
        "https://10.0.0.1/v1",
        "https://[fc00::1]/v1",
        "https://user:password@8.8.8.8/v1",
        "https://8.8.8.8/v1#fragment"
    })
    void 应拒绝不安全的供应商地址(String value) {
        assertThrows(UnsafeOutboundEndpointException.class, () -> policy.verify(URI.create(value)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://8.8.8.8/v1",
        "https://[2606:4700:4700::1111]/v1"
    })
    void 应允许没有歧义的公网地址(String value) {
        assertDoesNotThrow(() -> policy.verify(URI.create(value)));
    }
}

package com.aiplatform.shared.security.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StrictOutboundEndpointPolicyTest {
    @ParameterizedTest(name = "拒绝危险 endpoint: {0}")
    @MethodSource("dangerousEndpoints")
    void rejectsDangerousEndpoints(String endpoint) {
        StrictOutboundEndpointPolicy policy = policyResolvingTo("93.184.216.34");

        assertThatThrownBy(() -> policy.validateAndNormalize(endpoint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型供应商地址");
    }

    @ParameterizedTest(name = "拒绝非公网地址: {0}")
    @MethodSource("nonPublicAddresses")
    void rejectsEveryNonPublicAddressFamily(String address) {
        StrictOutboundEndpointPolicy policy = policyResolvingTo(address);

        assertThatThrownBy(() -> policy.validateAndNormalize("https://provider.example/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非公网地址");
    }

    @Test
    void acceptsAndNormalizesLegalPublicHttpsEndpoint() {
        StrictOutboundEndpointPolicy policy = policyResolvingTo("93.184.216.34");

        URI endpoint = policy.validateAndNormalize(" HTTPS://API.EXAMPLE.COM:443/v1/ ");

        assertThat(endpoint.toString()).isEqualTo("https://api.example.com/v1/");
    }

    @Test
    void acceptsThePublicShapedLiteralUsedOnlyInsideTheInternalAcceptanceProbeNetwork() {
        StrictOutboundEndpointPolicy policy = policyResolvingTo("203.1.113.2");

        URI endpoint = policy.validateAndNormalize("https://203.1.113.2/v1");

        assertThat(endpoint.toString()).isEqualTo("https://203.1.113.2/v1");
    }

    @Test
    void acceptsLegalPublicIpv6Literal() {
        StrictOutboundEndpointPolicy policy = policyResolvingTo("93.184.216.34");

        URI endpoint = policy.validateAndNormalize("https://[2606:4700:4700::1111]/v1");

        assertThat(endpoint.toString()).isEqualTo("https://[2606:4700:4700::1111]/v1");
    }

    @Test
    void rejectsMixedPublicAndPrivateDnsAnswersFailClosed() {
        StrictOutboundEndpointPolicy policy = new StrictOutboundEndpointPolicy(host -> new InetAddress[]{
                InetAddress.getByName("93.184.216.34"),
                InetAddress.getByName("10.0.0.8")
        });

        assertThatThrownBy(() -> policy.validateAndNormalize("https://provider.example/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非公网地址");
    }

    @Test
    void rejectsEmptyDnsAnswerFailClosed() {
        StrictOutboundEndpointPolicy policy = new StrictOutboundEndpointPolicy(host -> new InetAddress[0]);

        assertThatThrownBy(() -> policy.validateAndNormalize("https://provider.example/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DNS 未返回任何地址");
    }

    @Test
    void reResolvesAndRejectsDnsRebindingBeforeConnection() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        StrictOutboundEndpointPolicy policy = new StrictOutboundEndpointPolicy(host -> {
            String address = resolutions.getAndIncrement() == 0 ? "93.184.216.34" : "169.254.169.254";
            return new InetAddress[]{InetAddress.getByName(address)};
        });

        policy.validateAndNormalize("https://provider.example/v1");

        assertThatThrownBy(() -> policy.resolveForConnection("provider.example"))
                .isInstanceOf(UnknownHostException.class)
                .hasMessageContaining("非公网地址");
        assertThat(resolutions).hasValue(2);
    }

    private static Stream<Arguments> dangerousEndpoints() {
        return Stream.of(
                Arguments.of("local://deterministic"),
                Arguments.of("http://api.example.com/v1"),
                Arguments.of("https://api.example.com:8443/v1"),
                Arguments.of("https://user:secret@api.example.com/v1"),
                Arguments.of("https://api.example.com/v1?target=https://127.0.0.1"),
                Arguments.of("https://api.example.com/v1#fragment"),
                Arguments.of("https://localhost/v1"),
                Arguments.of("https://metadata.google.internal/computeMetadata/v1"),
                Arguments.of("https://127.0.0.1/v1"),
                Arguments.of("https://127.1/v1"),
                Arguments.of("https://0177.0.0.1/v1"),
                Arguments.of("https://0x7f000001/v1"),
                Arguments.of("https://2130706433/v1"),
                Arguments.of("https://127.0x0.0.1/v1"),
                Arguments.of("https://%31%32%37.0.0.1/v1"),
                Arguments.of("https://[::1]/v1"),
                Arguments.of("https://[::ffff:127.0.0.1]/v1"),
                Arguments.of("https://[fe80::1%25eth0]/v1"),
                Arguments.of("https://api.example.com./v1"),
                Arguments.of("https://例子.测试/v1")
        );
    }

    private static Stream<Arguments> nonPublicAddresses() {
        return Stream.of(
                Arguments.of("0.0.0.0"),
                Arguments.of("10.0.0.1"),
                Arguments.of("100.64.0.1"),
                Arguments.of("127.0.0.1"),
                Arguments.of("169.254.169.254"),
                Arguments.of("172.16.0.1"),
                Arguments.of("192.0.2.1"),
                Arguments.of("192.168.1.1"),
                Arguments.of("198.18.0.1"),
                Arguments.of("198.51.100.1"),
                Arguments.of("203.0.113.1"),
                Arguments.of("224.0.0.1"),
                Arguments.of("240.0.0.1"),
                Arguments.of("::"),
                Arguments.of("::1"),
                Arguments.of("fc00::1"),
                Arguments.of("fe80::1"),
                Arguments.of("ff02::1"),
                Arguments.of("64:ff9b::c000:201"),
                Arguments.of("100::1"),
                Arguments.of("2001:db8::1"),
                Arguments.of("2002:c000:0201::1"),
                Arguments.of("3fff::1")
        );
    }

    private static StrictOutboundEndpointPolicy policyResolvingTo(String address) {
        return new StrictOutboundEndpointPolicy(host -> new InetAddress[]{InetAddress.getByName(address)});
    }
}

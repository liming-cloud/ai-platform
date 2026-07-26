package com.aiplatform.foundation.security.egress;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * 仅允许解析到公网地址的 HTTPS 供应商端点。
 */
public final class PublicHttpsEndpointPolicy implements OutboundEndpointPolicy {

    @Override
    public void verify(URI endpoint) {
        Objects.requireNonNull(endpoint, "供应商地址不能为空");
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getHost() == null
                || endpoint.getHost().isBlank()
                || endpoint.getRawUserInfo() != null
                || endpoint.getRawFragment() != null
                || endpoint.getPort() == 0
                || endpoint.getPort() < -1
                || endpoint.getPort() > 65_535) {
            throw new UnsafeOutboundEndpointException("供应商地址必须是无用户信息和片段的有效 HTTPS 地址");
        }

        for (var address : resolve(endpoint.getHost())) {
            if (!isGlobalUnicast(address)) {
                throw new UnsafeOutboundEndpointException("供应商地址解析到禁止访问的网络");
            }
        }
    }

    private static InetAddress[] resolve(String host) {
        try {
            var addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw new UnsafeOutboundEndpointException("供应商地址无法解析");
            }
            return addresses;
        } catch (UnknownHostException exception) {
            throw new UnsafeOutboundEndpointException("供应商地址无法解析", exception);
        }
    }

    private static boolean isGlobalUnicast(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            return isGlobalIpv4(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            return isGlobalIpv6(address.getAddress());
        }
        return false;
    }

    private static boolean isGlobalIpv4(byte[] address) {
        var first = Byte.toUnsignedInt(address[0]);
        var second = Byte.toUnsignedInt(address[1]);
        var third = Byte.toUnsignedInt(address[2]);

        return first != 0
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 192 && second == 0 && third == 0)
                && !(first == 192 && second == 0 && third == 2)
                && !(first == 198 && (second == 18 || second == 19))
                && !(first == 198 && second == 51 && third == 100)
                && !(first == 203 && second == 0 && third == 113)
                && first < 224;
    }

    private static boolean isGlobalIpv6(byte[] address) {
        var first = Byte.toUnsignedInt(address[0]);
        var second = Byte.toUnsignedInt(address[1]);

        var uniqueLocal = (first & 0xfe) == 0xfc;
        var documentation = first == 0x20
                && second == 0x01
                && Byte.toUnsignedInt(address[2]) == 0x0d
                && Byte.toUnsignedInt(address[3]) == 0xb8;
        return !uniqueLocal && !documentation;
    }
}

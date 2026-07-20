package com.aiplatform.shared.security.net;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 默认拒绝的模型供应商 endpoint 策略：仅允许 HTTPS/443 和公网单播地址。
 */
public final class StrictOutboundEndpointPolicy implements OutboundEndpointPolicy {
    private static final Pattern AMBIGUOUS_NUMERIC_HOST = Pattern.compile(
            "(?i)(?:0x[0-9a-f]+|[0-9]+)(?:\\.(?:0x[0-9a-f]+|[0-9]+)){0,3}"
    );
    private static final Pattern CANONICAL_IPV4_SHAPE = Pattern.compile("[0-9]+(?:\\.[0-9]+){3}");
    private static final List<String> FORBIDDEN_HOST_SUFFIXES = List.of(".localhost", ".local", ".internal");

    private final HostnameResolver resolver;

    public StrictOutboundEndpointPolicy() {
        this(InetAddress::getAllByName);
    }

    public StrictOutboundEndpointPolicy(HostnameResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "主机名解析器不能为空");
    }

    @Override
    public URI validateAndNormalize(String endpoint) {
        String candidate = requireEndpoint(endpoint);
        URI uri = parse(candidate);
        validateUriShape(uri);
        String host = normalizeHost(uri.getHost(), uri.getRawAuthority());
        validateHostName(host);

        if (isIpv4Shape(host)) {
            validateLiteralAddress(parseCanonicalIpv4(host));
        } else if (host.indexOf(':') >= 0) {
            validateLiteralAddress(parseIpv6(host));
        } else {
            resolveForValidation(host);
        }
        return normalizedUri(uri, host);
    }

    @Override
    public List<InetAddress> resolveForConnection(String hostname) throws UnknownHostException {
        final String normalized;
        try {
            normalized = normalizeConnectionHost(hostname);
            validateHostName(normalized);
        } catch (IllegalArgumentException exception) {
            throw unknownHost(exception.getMessage(), exception);
        }

        InetAddress[] resolved = resolver.resolve(normalized);
        if (resolved == null || resolved.length == 0) {
            throw unknownHost("模型供应商地址 DNS 未返回任何地址", null);
        }
        for (InetAddress address : resolved) {
            if (!isPublicUnicast(address)) {
                throw unknownHost("模型供应商地址解析到非公网地址", null);
            }
        }
        return List.copyOf(Arrays.asList(resolved));
    }

    private void resolveForValidation(String host) {
        try {
            resolveForConnection(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("模型供应商地址无法安全解析：" + exception.getMessage(), exception);
        }
    }

    private static String requireEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("模型供应商地址不能为空");
        }
        return endpoint.trim();
    }

    private static URI parse(String endpoint) {
        try {
            return new URI(endpoint);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("模型供应商地址格式不合法", exception);
        }
    }

    private static void validateUriShape(URI uri) {
        if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("模型供应商地址仅允许使用 HTTPS");
        }
        if (uri.getRawAuthority() == null || uri.getRawAuthority().isBlank()) {
            throw new IllegalArgumentException("模型供应商地址必须包含主机名");
        }
        if (uri.getRawAuthority().indexOf('%') >= 0 || !isAscii(uri.getRawAuthority())) {
            throw new IllegalArgumentException("模型供应商地址不允许编码或 IDN authority");
        }
        if (uri.getRawUserInfo() != null || uri.getRawAuthority().indexOf('@') >= 0) {
            throw new IllegalArgumentException("模型供应商地址不允许包含 userinfo");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("模型供应商地址不允许包含 query 或 fragment");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new IllegalArgumentException("模型供应商地址端口仅允许 443");
        }
    }

    private static String normalizeHost(String parsedHost, String rawAuthority) {
        if (parsedHost == null || parsedHost.isBlank()) {
            throw new IllegalArgumentException("模型供应商地址主机名不合法");
        }
        String host = stripIpv6Brackets(parsedHost).toLowerCase(Locale.ROOT);
        if (host.endsWith(".") || host.contains("..")) {
            throw new IllegalArgumentException("模型供应商地址主机名不允许尾点或空标签");
        }
        if (host.indexOf(':') >= 0) {
            return host;
        }
        try {
            String ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (!ascii.equals(host) || rawAuthority.codePoints().anyMatch(codePoint -> codePoint > 0x7f)) {
                throw new IllegalArgumentException("模型供应商地址不允许 IDN 编码绕过");
            }
            return ascii;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("模型供应商地址")) {
                throw exception;
            }
            throw new IllegalArgumentException("模型供应商地址主机名不合法", exception);
        }
    }

    private static String normalizeConnectionHost(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            throw new IllegalArgumentException("模型供应商地址主机名不能为空");
        }
        String host = stripIpv6Brackets(hostname.trim()).toLowerCase(Locale.ROOT);
        if (!isAscii(host) || host.indexOf('%') >= 0) {
            throw new IllegalArgumentException("模型供应商地址主机名不合法");
        }
        if (host.indexOf(':') >= 0) {
            return host;
        }
        return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
    }

    private static void validateHostName(String host) {
        if (host.equals("localhost") || host.equals("local") || host.equals("metadata")
                || host.equals("instance-data") || FORBIDDEN_HOST_SUFFIXES.stream().anyMatch(host::endsWith)) {
            throw new IllegalArgumentException("模型供应商地址不允许本机或元数据主机名");
        }
        if (host.length() > 253 || host.endsWith(".") || host.contains("..")) {
            throw new IllegalArgumentException("模型供应商地址主机名不合法");
        }
        if (AMBIGUOUS_NUMERIC_HOST.matcher(host).matches() && !CANONICAL_IPV4_SHAPE.matcher(host).matches()) {
            throw new IllegalArgumentException("模型供应商地址不允许歧义数字 IP 表示");
        }
        if (CANONICAL_IPV4_SHAPE.matcher(host).matches()) {
            String[] components = host.split("\\.", -1);
            for (String component : components) {
                if ((component.length() > 1 && component.startsWith("0")) || component.length() > 3) {
                    throw new IllegalArgumentException("模型供应商地址不允许非规范 IPv4 表示");
                }
            }
        }
    }

    private static boolean isIpv4Shape(String host) {
        return CANONICAL_IPV4_SHAPE.matcher(host).matches();
    }

    private static InetAddress parseCanonicalIpv4(String host) {
        byte[] bytes = new byte[4];
        String[] components = host.split("\\.", -1);
        try {
            for (int index = 0; index < components.length; index++) {
                int value = Integer.parseInt(components[index]);
                if (value > 255) {
                    throw new NumberFormatException("IPv4 component overflow");
                }
                bytes[index] = (byte) value;
            }
            return InetAddress.getByAddress(bytes);
        } catch (NumberFormatException | UnknownHostException exception) {
            throw new IllegalArgumentException("模型供应商地址 IPv4 不合法", exception);
        }
    }

    private static InetAddress parseIpv6(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (!(address instanceof Inet6Address) && !(address instanceof Inet4Address)) {
                throw new IllegalArgumentException("模型供应商地址 IPv6 不合法");
            }
            return address;
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("模型供应商地址 IPv6 不合法", exception);
        }
    }

    private static void validateLiteralAddress(InetAddress address) {
        if (!isPublicUnicast(address)) {
            throw new IllegalArgumentException("模型供应商地址指向非公网地址");
        }
    }

    private static boolean isPublicUnicast(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address || bytes.length == 4) {
            return isPublicIpv4(bytes);
        }
        return address instanceof Inet6Address && isPublicIpv6(bytes);
    }

    private static boolean isPublicIpv4(byte[] bytes) {
        int value = ((bytes[0] & 0xff) << 24)
                | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xff);
        return !inIpv4Range(value, "0.0.0.0", 8)
                && !inIpv4Range(value, "10.0.0.0", 8)
                && !inIpv4Range(value, "100.64.0.0", 10)
                && !inIpv4Range(value, "127.0.0.0", 8)
                && !inIpv4Range(value, "169.254.0.0", 16)
                && !inIpv4Range(value, "172.16.0.0", 12)
                && !inIpv4Range(value, "192.0.0.0", 24)
                && !inIpv4Range(value, "192.0.2.0", 24)
                && !inIpv4Range(value, "192.31.196.0", 24)
                && !inIpv4Range(value, "192.52.193.0", 24)
                && !inIpv4Range(value, "192.88.99.0", 24)
                && !inIpv4Range(value, "192.168.0.0", 16)
                && !inIpv4Range(value, "192.175.48.0", 24)
                && !inIpv4Range(value, "198.18.0.0", 15)
                && !inIpv4Range(value, "198.51.100.0", 24)
                && !inIpv4Range(value, "203.0.113.0", 24)
                && !inIpv4Range(value, "224.0.0.0", 4)
                && !inIpv4Range(value, "240.0.0.0", 4);
    }

    private static boolean isPublicIpv6(byte[] bytes) {
        if (bytes.length != 16 || (bytes[0] & 0xe0) != 0x20) {
            return false;
        }
        return !hasPrefix(bytes, new byte[]{0x20, 0x01, 0x00}, 23)
                && !hasPrefix(bytes, new byte[]{0x20, 0x01, 0x0d, (byte) 0xb8}, 32)
                && !hasPrefix(bytes, new byte[]{0x20, 0x02}, 16)
                && !hasPrefix(bytes, new byte[]{0x3f, (byte) 0xff, 0x00}, 20);
    }

    private static boolean inIpv4Range(int value, String network, int prefixLength) {
        String[] components = network.split("\\.");
        int networkValue = 0;
        for (String component : components) {
            networkValue = (networkValue << 8) | Integer.parseInt(component);
        }
        int mask = prefixLength == 0 ? 0 : -1 << (32 - prefixLength);
        return (value & mask) == (networkValue & mask);
    }

    private static boolean hasPrefix(byte[] address, byte[] prefix, int prefixLength) {
        int wholeBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        for (int index = 0; index < wholeBytes; index++) {
            if (address[index] != prefix[index]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xff << (8 - remainingBits);
        return (address[wholeBytes] & mask) == (prefix[wholeBytes] & mask);
    }

    private static URI normalizedUri(URI source, String host) {
        String rawPath = source.getRawPath() == null ? "" : source.getRawPath();
        String authority = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
        return URI.create("https://" + authority + rawPath);
    }

    private static String stripIpv6Brackets(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static boolean isAscii(String value) {
        return StandardCharsets.US_ASCII.newEncoder().canEncode(value);
    }

    private static UnknownHostException unknownHost(String message, Throwable cause) {
        UnknownHostException exception = new UnknownHostException(message);
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }
}

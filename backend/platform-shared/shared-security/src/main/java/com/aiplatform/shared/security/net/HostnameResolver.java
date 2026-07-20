package com.aiplatform.shared.security.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 主机名解析 seam。生产环境使用系统解析器，测试可用确定性答案验证 DNS 重绑定边界。
 */
@FunctionalInterface
public interface HostnameResolver {
    InetAddress[] resolve(String hostname) throws UnknownHostException;
}

package com.aiplatform.identity.infrastructure.security.login;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录失败防护配置。默认五次失败、十五分钟固定窗口，且拒绝不安全边界。
 */
@ConfigurationProperties(prefix = "aip.identity.login-protection")
public class LoginProtectionProperties {
    private static final int MIN_FAILURES = 3;
    private static final int MAX_FAILURES = 20;
    private static final Duration MIN_WINDOW = Duration.ofMinutes(1);
    private static final Duration MAX_WINDOW = Duration.ofHours(24);

    private int maxFailures = 5;
    private Duration window = Duration.ofMinutes(15);

    public int getMaxFailures() {
        return maxFailures;
    }

    public void setMaxFailures(int maxFailures) {
        if (maxFailures < MIN_FAILURES || maxFailures > MAX_FAILURES) {
            throw new IllegalArgumentException("登录失败阈值必须在 3 到 20 之间");
        }
        this.maxFailures = maxFailures;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        if (window == null || window.compareTo(MIN_WINDOW) < 0 || window.compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("登录失败窗口必须在 1 分钟到 24 小时之间");
        }
        this.window = window;
    }
}

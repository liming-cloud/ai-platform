package com.aiplatform.foundation.security.satoken.configuration;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Sa-Token 人员会话的受控平台配置。
 */
@ConfigurationProperties("foundation.security")
public record SaTokenSecurityProperties(
        @DefaultValue("PT1H") Duration sessionTimeout,
        @DefaultValue("foundation-v1-person") String personLoginType) {

    public SaTokenSecurityProperties {
        Objects.requireNonNull(sessionTimeout, "会话有效期不能为空");
        Objects.requireNonNull(personLoginType, "人员登录类型不能为空");
        if (sessionTimeout.isZero()
                || sessionTimeout.isNegative()
                || sessionTimeout.getNano() != 0) {
            throw new IllegalArgumentException("会话有效期必须是正整数秒");
        }
        if (personLoginType.isBlank()
                || personLoginType.length() > 64
                || !personLoginType.equals(personLoginType.trim())) {
            throw new IllegalArgumentException("人员登录类型格式不正确");
        }
    }
}

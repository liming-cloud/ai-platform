package com.aiplatform.infra.springai.http;

import com.aiplatform.shared.security.net.OutboundEndpointPolicy;
import com.aiplatform.shared.security.net.StrictOutboundEndpointPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型出站地址安全策略配置。没有显式替代策略时始终启用严格公网 HTTPS 策略。
 */
@Configuration(proxyBeanMethods = false)
public class ModelEndpointSecurityConfiguration {
    @Bean
    @ConditionalOnMissingBean(OutboundEndpointPolicy.class)
    OutboundEndpointPolicy outboundEndpointPolicy() {
        return new StrictOutboundEndpointPolicy();
    }
}

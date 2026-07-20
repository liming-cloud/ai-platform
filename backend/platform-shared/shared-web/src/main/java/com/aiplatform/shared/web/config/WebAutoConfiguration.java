package com.aiplatform.shared.web.config;

import com.aiplatform.shared.web.filter.TraceTenantFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class WebAutoConfiguration {
    @Bean
    public FilterRegistrationBean<TraceTenantFilter> traceTenantFilter() {
        FilterRegistrationBean<TraceTenantFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceTenantFilter());
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(1);
        return registration;
    }
}

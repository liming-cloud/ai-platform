package org.fujay.ai.platform.security;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenWebMvcConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
                    if (!SaHolder.getRequest().isMethod("OPTIONS")) {
                        StpUtil.checkLogin();
                    }
                }))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login");
    }
}

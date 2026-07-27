package com.aiplatform.foundation.platform.web.autoconfigure;

import com.aiplatform.foundation.platform.web.error.FoundationExceptionHandler;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 注册统一且可由应用覆盖的 Web 异常处理能力。
 */
@AutoConfiguration
@ConditionalOnClass(RestControllerAdvice.class)
public class PlatformWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    FoundationExceptionHandler foundationExceptionHandler(
            EntityIdGenerator identifiers) {
        return new FoundationExceptionHandler(identifiers);
    }
}

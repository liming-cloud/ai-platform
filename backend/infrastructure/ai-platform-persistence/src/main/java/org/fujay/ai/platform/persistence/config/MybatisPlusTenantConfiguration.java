package org.fujay.ai.platform.persistence.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.fujay.ai.platform.shared.tenant.TenantContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusTenantConfiguration {

    private static final Set<String> TENANT_IGNORED_TABLES =
            Set.of("platform_tenant", "platform_provider_type", "platform_model_option");

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(String.valueOf(TenantContext.requiredTenantId().value()));
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return TENANT_IGNORED_TABLES.contains(tableName);
            }
        }));
        return interceptor;
    }
}

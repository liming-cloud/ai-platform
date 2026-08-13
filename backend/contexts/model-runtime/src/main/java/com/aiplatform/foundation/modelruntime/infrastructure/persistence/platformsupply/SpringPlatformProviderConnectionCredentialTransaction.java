package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply;

import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialTransaction;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用同一 Spring 事务提交平台连接根和平台密文凭据。 */
public final class SpringPlatformProviderConnectionCredentialTransaction
        implements PlatformProviderConnectionCredentialTransaction {

    private final TransactionTemplate template;

    public SpringPlatformProviderConnectionCredentialTransaction(PlatformTransactionManager transactions) {
        this.template = new TransactionTemplate(Objects.requireNonNull(transactions, "事务管理器不能为空"));
    }

    @Override
    public <T> T execute(Supplier<T> action) {
        Objects.requireNonNull(action, "事务动作不能为空");
        return template.execute(status -> action.get());
    }
}

package com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider;

import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderConnectionCredentialTransaction;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 Spring 事务将连接根和密文凭据作为一个原子写入单元。
 */
public final class SpringProviderConnectionCredentialTransaction
        implements ProviderConnectionCredentialTransaction {

    private final TransactionTemplate template;

    public SpringProviderConnectionCredentialTransaction(PlatformTransactionManager transactions) {
        this.template = new TransactionTemplate(Objects.requireNonNull(transactions, "事务管理器不能为空"));
    }

    @Override
    public <T> T execute(Supplier<T> action) {
        Objects.requireNonNull(action, "事务动作不能为空");
        return template.execute(status -> action.get());
    }
}

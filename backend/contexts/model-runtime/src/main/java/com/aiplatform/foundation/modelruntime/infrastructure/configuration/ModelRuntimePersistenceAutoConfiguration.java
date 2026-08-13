package com.aiplatform.foundation.modelruntime.infrastructure.configuration;

import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.application.deployment.port.out.ModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderConnectionCredentialTransaction;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.repository.PostgresProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialWriter;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationTransaction;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationAccessRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelDeploymentMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelDeploymentRequestMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelCapabilityVerificationMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.repository.PostgresModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.repository.PostgresModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ModelConfigurationMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ControlledModelCatalogEntryMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.PostgresModelConfigurationAccessRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ModelConfigurationAccessMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.PostgresModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.PostgresEncryptedModelCredentialStore;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ModelConfigurationCredentialMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.SpringModelConfigurationTransaction;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionRequestMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.repository.PostgresProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.PostgresEncryptedProviderConnectionCredentialStore;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.SpringProviderConnectionCredentialTransaction;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionCredentialMapper;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialTransaction;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.PostgresEncryptedPlatformProviderConnectionCredentialStore;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.SpringPlatformProviderConnectionCredentialTransaction;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository.PostgresPlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository.PostgresPlatformProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository.PostgresPlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository.PostgresPlatformModelDeploymentVerificationStore;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aiplatform.foundation.security.secret.SecretEncryptionKeyProvider;

/**
 * 注册模型目录 Mapper 和聚合仓储实现。
 */
@AutoConfiguration(after = MybatisPlusAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.model-runtime.persistence.enabled",
        havingValue = "true")
@ConditionalOnBean(SqlSessionFactory.class)
@MapperScan(basePackageClasses = {
    ProviderConnectionMapper.class,
    ModelDeploymentMapper.class,
    ModelConfigurationMapper.class
})
public class ModelRuntimePersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ProviderModelDiscoveryStore.class)
    PostgresProviderModelDiscoveryStore postgresProviderModelDiscoveryStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions) {
        return new PostgresProviderModelDiscoveryStore(jdbc, transactions);
    }

    @Bean
    @ConditionalOnMissingBean
    ProviderConnectionRepository providerConnectionRepository(
            ProviderConnectionMapper mapper,
            ProviderConnectionRequestMapper requestMapper,
            PlatformTransactionManager transactions) {
        return new PostgresProviderConnectionRepository(
                mapper,
                requestMapper,
                transactions);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformProviderConnectionRepository platformProviderConnectionRepository(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions) {
        return new PostgresPlatformProviderConnectionRepository(jdbc, transactions);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformProviderModelDiscoveryStore.class)
    PostgresPlatformProviderModelDiscoveryStore platformProviderModelDiscoveryStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions) {
        return new PostgresPlatformProviderModelDiscoveryStore(jdbc, transactions);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformModelDeploymentRepository platformModelDeploymentRepository(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions) {
        return new PostgresPlatformModelDeploymentRepository(jdbc, transactions);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformModelDeploymentVerificationStore platformModelDeploymentVerificationStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions) {
        return new PostgresPlatformModelDeploymentVerificationStore(jdbc, transactions);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformProviderConnectionCredentialTransaction platformProviderConnectionCredentialTransaction(
            PlatformTransactionManager transactions) {
        return new SpringPlatformProviderConnectionCredentialTransaction(transactions);
    }

    @Bean
    @ConditionalOnBean(SecretEncryptionKeyProvider.class)
    @ConditionalOnMissingBean(PlatformProviderConnectionCredentialWriter.class)
    PostgresEncryptedPlatformProviderConnectionCredentialStore platformProviderConnectionCredentialWriter(
            JdbcTemplate jdbc,
            SecretEncryptionKeyProvider keyProvider) {
        return new PostgresEncryptedPlatformProviderConnectionCredentialStore(jdbc, keyProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    ModelDeploymentRepository modelDeploymentRepository(
            ModelDeploymentMapper mapper,
            ModelDeploymentRequestMapper requestMapper,
            PlatformTransactionManager transactions) {
        return new PostgresModelDeploymentRepository(
                mapper,
                requestMapper,
                transactions);
    }

    @Bean
    @ConditionalOnMissingBean
    ModelDeploymentVerificationStore modelDeploymentVerificationStore(
            ModelDeploymentMapper deployments,
            ModelCapabilityVerificationMapper verifications,
            PlatformTransactionManager transactions) {
        return new PostgresModelDeploymentVerificationStore(
                deployments, verifications, transactions);
    }

    @Bean
    @ConditionalOnMissingBean
    ModelConfigurationRepository modelConfigurationRepository(
            ControlledModelCatalogEntryMapper catalogMapper,
            ModelConfigurationMapper configurationMapper) {
        return new PostgresModelConfigurationRepository(catalogMapper, configurationMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    ModelConfigurationAccessRepository modelConfigurationAccessRepository(
            ModelConfigurationAccessMapper mapper) {
        return new PostgresModelConfigurationAccessRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    ModelConfigurationTransaction modelConfigurationTransaction(
            PlatformTransactionManager transactions) {
        return new SpringModelConfigurationTransaction(transactions);
    }

    @Bean
    @ConditionalOnMissingBean
    ProviderConnectionCredentialTransaction providerConnectionCredentialTransaction(
            PlatformTransactionManager transactions) {
        return new SpringProviderConnectionCredentialTransaction(transactions);
    }

    @Bean
    @ConditionalOnBean(SecretEncryptionKeyProvider.class)
    @ConditionalOnMissingBean(ProviderConnectionCredentialWriter.class)
    PostgresEncryptedProviderConnectionCredentialStore providerConnectionCredentialWriter(
            ProviderConnectionCredentialMapper mapper,
            SecretEncryptionKeyProvider keyProvider) {
        return new PostgresEncryptedProviderConnectionCredentialStore(mapper, keyProvider);
    }

    @Bean
    @ConditionalOnBean(SecretEncryptionKeyProvider.class)
    @ConditionalOnMissingBean(ModelConfigurationCredentialWriter.class)
    PostgresEncryptedModelCredentialStore modelConfigurationCredentialWriter(
            ModelConfigurationCredentialMapper mapper,
            SecretEncryptionKeyProvider keyProvider) {
        return new PostgresEncryptedModelCredentialStore(mapper, keyProvider);
    }
}

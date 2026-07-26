package com.aiplatform.foundation.queryruntime;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.StpLogic;
import com.aiplatform.foundation.assistantdelivery.adapter.out.persistence.AssistantMapper;
import com.aiplatform.foundation.assistantdelivery.adapter.out.persistence.PostgresAssistantRepository;
import com.aiplatform.foundation.assistantdelivery.api.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.api.QueryAssistantCatalogUseCase;
import com.aiplatform.foundation.assistantdelivery.application.DefaultQueryAssistantCatalogService;
import com.aiplatform.foundation.assistantdelivery.application.DefaultPublishedAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantRepository;
import com.aiplatform.foundation.modelruntime.adapter.out.persistence.ModelDeploymentMapper;
import com.aiplatform.foundation.modelruntime.adapter.out.persistence.PostgresModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.adapter.out.persistence.PostgresProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.adapter.out.persistence.ProviderConnectionMapper;
import com.aiplatform.foundation.modelruntime.api.extension.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.api.extension.EmbeddingModelFactory;
import com.aiplatform.foundation.modelruntime.api.invocation.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.api.invocation.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.application.runtime.DefaultModelRuntimeResolver;
import com.aiplatform.foundation.organizationaccess.adapter.out.session.SaTokenSessionGateway;
import com.aiplatform.foundation.organizationaccess.application.port.out.AuthenticatedSessionGateway;
import com.aiplatform.foundation.provider.deterministic.DeterministicChatModelFactory;
import com.aiplatform.foundation.provider.deterministic.DeterministicEmbeddingModel;
import com.aiplatform.foundation.provider.deterministic.DeterministicEmbeddingModelFactory;
import com.aiplatform.foundation.trustedquery.adapter.out.persistence.PostgresQueryRunRepository;
import com.aiplatform.foundation.trustedquery.adapter.out.persistence.QueryRunMapper;
import com.aiplatform.foundation.trustedquery.api.CandidateSearchUseCase;
import com.aiplatform.foundation.trustedquery.api.QueryRunEventUseCase;
import com.aiplatform.foundation.trustedquery.api.QueryRunUseCase;
import com.aiplatform.foundation.trustedquery.application.DefaultCandidateSearchService;
import com.aiplatform.foundation.trustedquery.application.DefaultQueryRunEventService;
import com.aiplatform.foundation.trustedquery.application.DefaultQueryRunService;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryQuotaPolicy;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunRepository;
import com.aiplatform.foundation.vectorstore.pgvector.PgVectorIndexStore;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 在线查询进程的会话恢复、嵌入和向量检索显式装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "foundation.query.enabled",
        havingValue = "true")
@MapperScan(basePackageClasses = {
    AssistantMapper.class,
    ProviderConnectionMapper.class,
    QueryRunMapper.class
})
public class QueryRuntimeConfiguration {

    private static final String PERSON_LOGIN_TYPE = "foundation-v1-person";

    @Bean
    AuthenticatedSessionGateway authenticatedSessionGateway(
            SaTokenDao saTokenDao,
            @Value("${foundation.security.session-timeout:PT1H}") Duration timeout) {
        SaManager.setSaTokenDao(saTokenDao);
        return new SaTokenSessionGateway(new StpLogic(PERSON_LOGIN_TYPE), timeout);
    }

    @Bean
    PgVectorIndexStore pgVectorIndexStore(JdbcTemplate jdbc) {
        return new PgVectorIndexStore(jdbc);
    }

    @Bean
    EmbeddingModelPort embeddingModel() {
        return new DeterministicEmbeddingModel();
    }

    @Bean
    CandidateSearchUseCase candidateSearchUseCase(
            EmbeddingModelPort embeddingModel,
            PgVectorIndexStore candidates) {
        return new DefaultCandidateSearchService(
                embeddingModel,
                new EmbeddingDeploymentRef(
                        DeterministicEmbeddingModel.DEPLOYMENT_ID,
                        DeterministicEmbeddingModel.DEPLOYMENT_REVISION,
                        DeterministicEmbeddingModel.DIMENSION),
                candidates);
    }

    @Bean
    AssistantRepository queryAssistantRepository(
            AssistantMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresAssistantRepository(mapper, transactions);
    }

    @Bean
    PublishedAssistantVersionLookup queryPublishedAssistantVersionLookup(
            AssistantRepository repository) {
        return new DefaultPublishedAssistantVersionLookup(repository);
    }

    @Bean
    QueryAssistantCatalogUseCase queryAssistantCatalogUseCase(
            AssistantRepository repository) {
        return new DefaultQueryAssistantCatalogService(repository);
    }

    @Bean
    ProviderConnectionRepository queryProviderConnectionRepository(
            ProviderConnectionMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresProviderConnectionRepository(mapper, transactions);
    }

    @Bean
    ModelDeploymentRepository queryModelDeploymentRepository(
            ModelDeploymentMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresModelDeploymentRepository(mapper, transactions);
    }

    @Bean
    ModelRuntimeResolver queryModelRuntimeResolver(
            ModelDeploymentRepository deployments,
            ProviderConnectionRepository providers,
            @Value("${foundation.model-runtime.cache.maximum-size:64}")
                    int maximumCacheSize,
            @Value("${foundation.model-runtime.cache.idle-timeout:PT30M}")
                    Duration idleTimeout) {
        List<ChatModelFactory> chatFactories =
                List.of(new DeterministicChatModelFactory());
        List<EmbeddingModelFactory> embeddingFactories =
                List.of(new DeterministicEmbeddingModelFactory());
        return new DefaultModelRuntimeResolver(
                deployments,
                providers,
                chatFactories,
                embeddingFactories,
                maximumCacheSize,
                idleTimeout,
                Clock.systemUTC());
    }

    @Bean
    QueryRunRepository queryRunRepository(
            QueryRunMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresQueryRunRepository(mapper, transactions);
    }

    @Bean
    QueryRunUseCase queryRunUseCase(
            PublishedAssistantVersionLookup assistants,
            ModelRuntimeResolver models,
            PgVectorIndexStore candidates,
            QueryRunRepository repository,
            @Value("${foundation.query.quota.daily-limit:100}")
                    int dailyQueryLimit,
            @Value("${foundation.query.quota.concurrent-limit:4}")
                    int concurrentQueryLimit,
            @Value("${foundation.query.quota.running-lease:PT2M}")
                    Duration runningLease) {
        return new DefaultQueryRunService(
                assistants,
                models,
                candidates,
                repository,
                new QueryQuotaPolicy(
                        dailyQueryLimit,
                        concurrentQueryLimit,
                        runningLease),
                Clock.systemUTC(),
                UUID::randomUUID);
    }

    @Bean
    QueryRunEventUseCase queryRunEventUseCase(QueryRunRepository repository) {
        return new DefaultQueryRunEventService(repository);
    }
}

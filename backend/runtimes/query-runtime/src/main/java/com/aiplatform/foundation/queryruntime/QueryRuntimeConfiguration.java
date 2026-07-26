package com.aiplatform.foundation.queryruntime;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.StpLogic;
import com.aiplatform.foundation.modelruntime.api.invocation.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingDeploymentRef;
import com.aiplatform.foundation.organizationaccess.adapter.out.session.SaTokenSessionGateway;
import com.aiplatform.foundation.organizationaccess.application.port.out.AuthenticatedSessionGateway;
import com.aiplatform.foundation.provider.deterministic.DeterministicEmbeddingModel;
import com.aiplatform.foundation.trustedquery.api.CandidateSearchUseCase;
import com.aiplatform.foundation.trustedquery.application.DefaultCandidateSearchService;
import com.aiplatform.foundation.vectorstore.pgvector.PgVectorIndexStore;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 在线查询进程的会话恢复、嵌入和向量检索显式装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "foundation.query.enabled",
        havingValue = "true")
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
}

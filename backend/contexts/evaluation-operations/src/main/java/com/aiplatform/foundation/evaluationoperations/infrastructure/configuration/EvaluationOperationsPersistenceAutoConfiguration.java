package com.aiplatform.foundation.evaluationoperations.infrastructure.configuration;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetRegistrationStore;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetCandidateDirectory;
import com.aiplatform.foundation.evaluationoperations.application.operations.port.out.EvaluationOperationsFactSource;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationDatasetRepository;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationRunReleaseFactLookup;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationRunRepository;
import com.aiplatform.foundation.evaluationoperations.domain.release.repository.ReleaseDecisionRepository;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.mapper.EvaluationDatasetMapper;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.mapper.EvaluationRunMapper;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.mapper.ReleaseDecisionMapper;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository.PostgresEvaluationDatasetRepository;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository.JdbcEvaluationDatasetCandidateDirectory;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository.PostgresEvaluationDatasetRegistrationStore;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository.PostgresEvaluationRunRepository;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository.PostgresEvaluationRunReleaseFactLookup;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository.PostgresReleaseDecisionRepository;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.operations.JdbcEvaluationOperationsFactSource;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 注册评测清单持久化；只有显式开启数据库能力时才扫描 Mapper。
 */
@AutoConfiguration(after = {MybatisPlusAutoConfiguration.class, JdbcClientAutoConfiguration.class})
@ConditionalOnProperty(
        name = "foundation.evaluation-operations.persistence.enabled",
        havingValue = "true")
@ConditionalOnBean(SqlSessionFactory.class)
@MapperScan(basePackageClasses = EvaluationDatasetMapper.class)
public class EvaluationOperationsPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcClient.class)
    EvaluationDatasetCandidateDirectory evaluationDatasetCandidateDirectory(JdbcClient jdbc) {
        return new JdbcEvaluationDatasetCandidateDirectory(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcClient.class)
    EvaluationOperationsFactSource evaluationOperationsFactSource(JdbcClient jdbc) {
        return new JdbcEvaluationOperationsFactSource(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    EvaluationDatasetRegistrationStore evaluationDatasetRegistrationStore(EvaluationDatasetMapper mapper) {
        return new PostgresEvaluationDatasetRegistrationStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    EvaluationDatasetRepository evaluationDatasetRepository(EvaluationDatasetMapper mapper) {
        return new PostgresEvaluationDatasetRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    EvaluationRunRepository evaluationRunRepository(EvaluationRunMapper mapper) {
        return new PostgresEvaluationRunRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    EvaluationRunReleaseFactLookup evaluationRunReleaseFactLookup(EvaluationRunMapper mapper) {
        return new PostgresEvaluationRunReleaseFactLookup(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    ReleaseDecisionRepository releaseDecisionRepository(ReleaseDecisionMapper mapper) {
        return new PostgresReleaseDecisionRepository(mapper);
    }
}

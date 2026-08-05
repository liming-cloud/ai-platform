package com.aiplatform.foundation.evaluationoperations.infrastructure.configuration;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetRegistrationStore;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationDatasetRepository;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationRunRepository;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.mapper.EvaluationDatasetMapper;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.mapper.EvaluationRunMapper;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository.PostgresEvaluationDatasetRepository;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository.PostgresEvaluationDatasetRegistrationStore;
import com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository.PostgresEvaluationRunRepository;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 注册评测清单持久化；只有显式开启数据库能力时才扫描 Mapper。
 */
@AutoConfiguration(after = MybatisPlusAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.evaluation-operations.persistence.enabled",
        havingValue = "true")
@ConditionalOnBean(SqlSessionFactory.class)
@MapperScan(basePackageClasses = EvaluationDatasetMapper.class)
public class EvaluationOperationsPersistenceAutoConfiguration {

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
}

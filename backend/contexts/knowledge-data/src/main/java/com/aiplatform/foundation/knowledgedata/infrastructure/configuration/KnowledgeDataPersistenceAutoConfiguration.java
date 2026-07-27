package com.aiplatform.foundation.knowledgedata.infrastructure.configuration;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.TextDataSourceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.repository.PostgresTextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexVersionMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.repository.PostgresIndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.mapper.KnowledgeSpaceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.repository.PostgresKnowledgeSpaceRepository;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 注册知识数据上下文的 MyBatis Mapper 和持久化端口实现。
 */
@AutoConfiguration(after = MybatisPlusAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.knowledge-data.persistence.enabled",
        havingValue = "true")
@ConditionalOnBean(SqlSessionFactory.class)
@MapperScan(basePackageClasses = {
    KnowledgeSpaceMapper.class,
    TextDataSourceMapper.class,
    IndexVersionMapper.class
})
public class KnowledgeDataPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    KnowledgeSpaceRepository knowledgeSpaceRepository(
            KnowledgeSpaceMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresKnowledgeSpaceRepository(mapper, transactions);
    }

    @Bean
    @ConditionalOnMissingBean
    TextDataSourceRepository textDataSourceRepository(
            TextDataSourceMapper mapper,
            PlatformTransactionManager transactions,
            EntityIdGenerator identifiers) {
        return new PostgresTextDataSourceRepository(
                mapper,
                transactions,
                identifiers);
    }

    @Bean
    @ConditionalOnMissingBean
    IndexVersionRepository indexVersionRepository(
            IndexVersionMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresIndexVersionRepository(mapper, transactions);
    }
}

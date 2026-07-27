package com.aiplatform.foundation.vectorstore.pgvector.autoconfigure;

import com.aiplatform.foundation.vectorstore.pgvector.PgVectorIndexStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 注册 pgvector 向量写入与候选检索实现。
 */
@AutoConfiguration
@ConditionalOnProperty(
        name = "foundation.vector-store.pgvector.enabled",
        havingValue = "true")
public class PgVectorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    PgVectorIndexStore pgVectorIndexStore(JdbcTemplate jdbc) {
        return new PgVectorIndexStore(jdbc);
    }
}

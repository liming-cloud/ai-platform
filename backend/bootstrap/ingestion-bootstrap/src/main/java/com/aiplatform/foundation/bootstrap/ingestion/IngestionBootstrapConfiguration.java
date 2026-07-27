package com.aiplatform.foundation.bootstrap.ingestion;

import com.aiplatform.foundation.bootstrap.ingestion.configuration.IndexIngestionConfiguration;
import com.aiplatform.foundation.bootstrap.ingestion.configuration.IngestionModelRuntimeConfiguration;
import com.aiplatform.foundation.knowledgedata.adapter.in.scheduling.IndexBuildScheduler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 索引摄取进程的唯一跨上下文装配入口。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Import({
    IndexIngestionConfiguration.class,
    IngestionModelRuntimeConfiguration.class,
    IndexBuildScheduler.class
})
public class IngestionBootstrapConfiguration {
}

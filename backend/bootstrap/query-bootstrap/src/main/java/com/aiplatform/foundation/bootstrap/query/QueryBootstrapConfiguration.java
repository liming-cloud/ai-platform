package com.aiplatform.foundation.bootstrap.query;

import com.aiplatform.foundation.bootstrap.query.configuration.QueryRuntimeConfiguration;
import com.aiplatform.foundation.bootstrap.query.web.QueryApiExceptionHandler;
import com.aiplatform.foundation.trustedquery.adapter.in.web.CandidateSearchController;
import com.aiplatform.foundation.trustedquery.adapter.in.web.QueryAssistantController;
import com.aiplatform.foundation.trustedquery.adapter.in.web.QueryRunController;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 在线查询进程的唯一跨上下文装配入口。
 */
@Configuration(proxyBeanMethods = false)
@Import({
    QueryRuntimeConfiguration.class,
    CandidateSearchController.class,
    QueryAssistantController.class,
    QueryRunController.class,
    QueryApiExceptionHandler.class
})
public class QueryBootstrapConfiguration {
}

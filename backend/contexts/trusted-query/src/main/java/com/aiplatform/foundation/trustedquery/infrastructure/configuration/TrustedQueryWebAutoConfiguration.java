package com.aiplatform.foundation.trustedquery.infrastructure.configuration;

import com.aiplatform.foundation.trustedquery.interfaces.web.query.controller.CandidateSearchController;
import com.aiplatform.foundation.trustedquery.interfaces.web.query.controller.QueryAssistantController;
import com.aiplatform.foundation.trustedquery.interfaces.web.query.controller.QueryRunController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * 显式注册在线查询 HTTP 入口。
 */
@AutoConfiguration(after = TrustedQueryCoreAutoConfiguration.class)
@ConditionalOnProperty(name = "foundation.query.enabled", havingValue = "true")
@Import({
    CandidateSearchController.class,
    QueryAssistantController.class,
    QueryRunController.class
})
public class TrustedQueryWebAutoConfiguration {
}

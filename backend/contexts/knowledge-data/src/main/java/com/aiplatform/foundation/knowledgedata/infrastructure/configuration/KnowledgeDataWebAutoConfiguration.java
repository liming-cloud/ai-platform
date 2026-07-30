package com.aiplatform.foundation.knowledgedata.infrastructure.configuration;

import com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller.TextDataSourceController;
import com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller.GovernedFileDataSourceController;
import com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller.GovernedObjectStorageDataSourceController;
import com.aiplatform.foundation.knowledgedata.interfaces.web.indexversion.controller.IndexVersionController;
import com.aiplatform.foundation.knowledgedata.interfaces.web.knowledge.controller.KnowledgeSpaceController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * 在知识数据控制面启用时显式注册 HTTP 入口。
 */
@AutoConfiguration(after = KnowledgeDataCoreAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.knowledge-data.enabled",
        havingValue = "true")
@Import({
    KnowledgeSpaceController.class,
    TextDataSourceController.class,
    GovernedFileDataSourceController.class,
    GovernedObjectStorageDataSourceController.class,
    IndexVersionController.class
})
public class KnowledgeDataWebAutoConfiguration {
}

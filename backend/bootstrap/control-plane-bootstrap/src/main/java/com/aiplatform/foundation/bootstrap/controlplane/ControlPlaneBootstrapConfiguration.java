package com.aiplatform.foundation.bootstrap.controlplane;

import com.aiplatform.foundation.assistantdelivery.adapter.in.web.AssistantController;
import com.aiplatform.foundation.assistantdelivery.adapter.in.web.AssistantVersionController;
import com.aiplatform.foundation.bootstrap.controlplane.configuration.AssistantDeliveryConfiguration;
import com.aiplatform.foundation.bootstrap.controlplane.configuration.EvaluationOperationsConfiguration;
import com.aiplatform.foundation.bootstrap.controlplane.configuration.KnowledgeDataConfiguration;
import com.aiplatform.foundation.bootstrap.controlplane.configuration.ModelCatalogConfiguration;
import com.aiplatform.foundation.bootstrap.controlplane.configuration.ModelRuntimeConfiguration;
import com.aiplatform.foundation.bootstrap.controlplane.web.GlobalApiExceptionHandler;
import com.aiplatform.foundation.evaluationoperations.adapter.in.web.AuditRecordController;
import com.aiplatform.foundation.evaluationoperations.adapter.in.web.UsageController;
import com.aiplatform.foundation.knowledgedata.adapter.in.web.IndexVersionController;
import com.aiplatform.foundation.knowledgedata.adapter.in.web.KnowledgeSpaceController;
import com.aiplatform.foundation.knowledgedata.adapter.in.web.TextDataSourceController;
import com.aiplatform.foundation.modelruntime.adapter.in.web.ModelDeploymentController;
import com.aiplatform.foundation.modelruntime.adapter.in.web.ProviderConnectionController;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 管理控制面的唯一跨上下文装配入口。
 */
@Configuration(proxyBeanMethods = false)
@Import({
    KnowledgeDataConfiguration.class,
    ModelCatalogConfiguration.class,
    ModelRuntimeConfiguration.class,
    AssistantDeliveryConfiguration.class,
    EvaluationOperationsConfiguration.class,
    KnowledgeSpaceController.class,
    TextDataSourceController.class,
    IndexVersionController.class,
    ProviderConnectionController.class,
    ModelDeploymentController.class,
    AssistantController.class,
    AssistantVersionController.class,
    AuditRecordController.class,
    UsageController.class,
    GlobalApiExceptionHandler.class
})
public class ControlPlaneBootstrapConfiguration {
}

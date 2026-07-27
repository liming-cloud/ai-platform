package com.aiplatform.foundation.modelruntime.api.catalog;

import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.CreateModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.CreateModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.ListModelDeploymentsQuery;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.ModelDeploymentPage;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.ModelDeploymentView;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.ProbeModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.ProbeModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.api.catalog.model.deployment.PublishModelDeploymentCommand;

/**
 * 模型部署创建、探测和发布的稳定公共用例。
 */
public interface ModelDeploymentUseCase {

    CreateModelDeploymentResult create(CreateModelDeploymentCommand command);

    ModelDeploymentPage list(ListModelDeploymentsQuery query);

    ProbeModelDeploymentResult probe(ProbeModelDeploymentCommand command);

    ModelDeploymentView publish(PublishModelDeploymentCommand command);
}

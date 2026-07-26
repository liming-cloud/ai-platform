package com.aiplatform.foundation.modelruntime.api.catalog;

import com.aiplatform.foundation.modelruntime.api.catalog.model.CreateModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.CreateModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ListModelDeploymentsQuery;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ModelDeploymentPage;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ModelDeploymentView;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ProbeModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ProbeModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.api.catalog.model.PublishModelDeploymentCommand;

/**
 * 模型部署创建、探测和发布的稳定公共用例。
 */
public interface ModelDeploymentUseCase {

    CreateModelDeploymentResult create(CreateModelDeploymentCommand command);

    ModelDeploymentPage list(ListModelDeploymentsQuery query);

    ProbeModelDeploymentResult probe(ProbeModelDeploymentCommand command);

    ModelDeploymentView publish(PublishModelDeploymentCommand command);
}

package com.aiplatform.foundation.bootstrap.controlplane;

import com.aiplatform.foundation.bootstrap.controlplane.configuration.EvaluationOperationsConfiguration;
import com.aiplatform.foundation.bootstrap.controlplane.web.GlobalApiExceptionHandler;
import com.aiplatform.foundation.evaluationoperations.adapter.in.web.AuditRecordController;
import com.aiplatform.foundation.evaluationoperations.adapter.in.web.UsageController;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 管理控制面的唯一跨上下文装配入口。
 */
@Configuration(proxyBeanMethods = false)
@Import({
    EvaluationOperationsConfiguration.class,
    AuditRecordController.class,
    UsageController.class,
    GlobalApiExceptionHandler.class
})
public class ControlPlaneBootstrapConfiguration {
}

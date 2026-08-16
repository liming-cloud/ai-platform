package com.aiplatform.foundation.evaluationoperations.infrastructure.configuration;

import com.aiplatform.foundation.evaluationoperations.interfaces.web.operations.controller.AuditRecordController;
import com.aiplatform.foundation.evaluationoperations.interfaces.web.operations.controller.UsageController;
import com.aiplatform.foundation.evaluationoperations.interfaces.web.operations.controller.OperationsOverviewController;
import com.aiplatform.foundation.evaluationoperations.interfaces.web.evaluation.controller.EvaluationDatasetController;
import com.aiplatform.foundation.evaluationoperations.interfaces.web.evaluation.controller.EvaluationDatasetCandidateController;
import com.aiplatform.foundation.evaluationoperations.interfaces.web.evaluation.controller.EvaluationRunController;
import com.aiplatform.foundation.evaluationoperations.interfaces.web.release.controller.ReleaseDecisionController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * 显式注册控制面的查询用量与脱敏审计入口。
 */
@AutoConfiguration(after = EvaluationOperationsCoreAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.evaluation-operations.enabled",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnProperty(
        name = "foundation.control-plane.enabled",
        havingValue = "true")
@Import({
    AuditRecordController.class,
    UsageController.class,
    OperationsOverviewController.class,
    EvaluationDatasetController.class,
    EvaluationDatasetCandidateController.class,
    EvaluationRunController.class,
    ReleaseDecisionController.class
})
public class EvaluationOperationsWebAutoConfiguration {
}

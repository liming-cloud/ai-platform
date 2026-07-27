package com.aiplatform.foundation.evaluationoperations.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.evaluationoperations.application.operations.service.OperationsQueryService;
import com.aiplatform.foundation.evaluationoperations.interfaces.web.operations.controller.AuditRecordController;
import com.aiplatform.foundation.evaluationoperations.interfaces.web.operations.controller.UsageController;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsFactSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EvaluationOperationsAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            EvaluationOperationsCoreAutoConfiguration.class,
                            EvaluationOperationsWebAutoConfiguration.class))
                    .withBean(
                            QueryOperationsFactSource.class,
                            () -> mock(QueryOperationsFactSource.class));

    @Test
    void 控制面角色开启时应注册运营服务和入口() {
        runner.withPropertyValues(
                        "foundation.control-plane.enabled=true",
                        "foundation.evaluation-operations.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(OperationsQueryService.class);
                    assertThat(context).hasSingleBean(AuditRecordController.class);
                    assertThat(context).hasSingleBean(UsageController.class);
                });
    }

    @Test
    void 非控制面角色不得注册运营能力() {
        runner.withPropertyValues(
                        "foundation.control-plane.enabled=false",
                        "foundation.evaluation-operations.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OperationsQueryService.class);
                    assertThat(context).doesNotHaveBean(AuditRecordController.class);
                    assertThat(context).doesNotHaveBean(UsageController.class);
                });
    }

    @Test
    void 用户提供应用服务时自动配置必须退让() {
        var custom = mock(OperationsQueryService.class);
        runner.withPropertyValues(
                        "foundation.control-plane.enabled=true",
                        "foundation.evaluation-operations.enabled=true")
                .withBean(OperationsQueryService.class, () -> custom)
                .run(context -> assertThat(
                                context.getBean(OperationsQueryService.class))
                        .isSameAs(custom));
    }

    @Test
    void 必需事实源缺失时应明确启动失败() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        EvaluationOperationsCoreAutoConfiguration.class))
                .withPropertyValues(
                        "foundation.control-plane.enabled=true",
                        "foundation.evaluation-operations.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}

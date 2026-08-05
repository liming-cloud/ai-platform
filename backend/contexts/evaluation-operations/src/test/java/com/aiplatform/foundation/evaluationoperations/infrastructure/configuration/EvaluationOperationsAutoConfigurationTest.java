package com.aiplatform.foundation.evaluationoperations.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetPrivateContentStore;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetContentStagingPort;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetEntryReader;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetRegistrationStore;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetRegistrationContent;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.service.EvaluationDatasetRegistrationService;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.service.EvaluationRunExecutionService;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationDatasetRepository;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationRunRepository;
import com.aiplatform.foundation.assistantdelivery.application.evaluation.facade.EvaluationAssistantExecution;
import com.aiplatform.foundation.evaluationoperations.application.operations.service.OperationsQueryService;
import com.aiplatform.foundation.evaluationoperations.interfaces.web.operations.controller.AuditRecordController;
import com.aiplatform.foundation.evaluationoperations.interfaces.web.operations.controller.UsageController;
import com.aiplatform.foundation.evaluationoperations.interfaces.web.evaluation.controller.EvaluationDatasetController;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsFactSource;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
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

    @Test
    void 只有同时具备私有内容和清单存储时才注册登记服务() {
        runner.withPropertyValues(
                        "foundation.control-plane.enabled=true",
                        "foundation.evaluation-operations.enabled=true")
                .withBean(EvaluationDatasetRegistrationStore.class,
                        () -> dataset -> { })
                .withBean(EvaluationDatasetPrivateContentStore.class,
                        EvaluationOperationsAutoConfigurationTest::privateContents)
                .withBean(EvaluationDatasetContentStagingPort.class,
                        () -> input -> { throw new UnsupportedOperationException(); })
                .withBean(EntityIdGenerator.class, () -> () -> 1L)
                .run(context -> assertThat(context)
                        .hasSingleBean(EvaluationDatasetRegistrationService.class)
                        .hasSingleBean(EvaluationDatasetController.class));
    }

    @Test
    void 缺少任一私有存储依赖时不得注册可用的登记服务() {
        runner.withPropertyValues(
                        "foundation.control-plane.enabled=true",
                        "foundation.evaluation-operations.enabled=true")
                .withBean(EvaluationDatasetRegistrationStore.class,
                        () -> dataset -> { })
                .withBean(EntityIdGenerator.class, () -> () -> 1L)
                .run(context -> assertThat(context)
                        .doesNotHaveBean(EvaluationDatasetRegistrationService.class)
                        .doesNotHaveBean(EvaluationDatasetController.class));
    }

    @Test
    void 仅在全部受限运行依赖可用时才注册评测执行服务() {
        runner.withPropertyValues(
                        "foundation.control-plane.enabled=true",
                        "foundation.evaluation-operations.enabled=true")
                .withBean(EvaluationDatasetRepository.class, () -> (tenantId, id) -> java.util.Optional.empty())
                .withBean(EvaluationRunRepository.class, () -> run -> { })
                .withBean(EvaluationDatasetPrivateContentStore.class,
                        EvaluationOperationsAutoConfigurationTest::privateContents)
                .withBean(EvaluationDatasetEntryReader.class, () -> (content, consumer) -> 0)
                .withBean(EvaluationAssistantExecution.class, () -> mock(EvaluationAssistantExecution.class))
                .withBean(EntityIdGenerator.class, () -> () -> 1L)
                .run(context -> assertThat(context).hasSingleBean(EvaluationRunExecutionService.class));
    }

    @Test
    void 未显式启用时不得连接或注册评测私有对象存储() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        EvaluationOperationsObjectStorageAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(EvaluationDatasetPrivateContentStore.class));
    }

    @Test
    void 启用私有对象存储时应注册受限内容暂存端口() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        EvaluationOperationsContentStagingAutoConfiguration.class))
                .withPropertyValues(
                        "foundation.evaluation-operations.object-storage.enabled=true",
                        "foundation.evaluation-operations.registration.staging-directory=target/evaluation-staging-test",
                        "foundation.evaluation-operations.registration.maximum-content-bytes=1024")
                .run(context -> assertThat(context)
                        .hasSingleBean(EvaluationDatasetContentStagingPort.class)
                        .hasSingleBean(EvaluationDatasetEntryReader.class));
    }

    @Test
    void 自定义私有内容存储应使MinIO自动配置完全退让() {
        var custom = privateContents();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        EvaluationOperationsObjectStorageAutoConfiguration.class))
                .withPropertyValues("foundation.evaluation-operations.object-storage.enabled=true")
                .withBean(EvaluationDatasetPrivateContentStore.class, () -> custom)
                .run(context -> assertThat(context.getBean(EvaluationDatasetPrivateContentStore.class))
                        .isSameAs(custom));
    }

    private static EvaluationDatasetPrivateContentStore privateContents() {
        return new EvaluationDatasetPrivateContentStore() {
            @Override
            public String put(
                    TenantId tenantId,
                    long datasetId,
                    EvaluationDatasetRegistrationContent content) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.io.InputStream open(String privateContentReference) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(String privateContentReference) {
                throw new UnsupportedOperationException();
            }
        };
    }
}

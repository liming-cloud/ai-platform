package com.aiplatform.foundation.evaluationoperations.application.evaluation.service;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetContentStagingPort;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetPrivateContentStore;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetRegistrationStore;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetRegistrationContent;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetRegistrationRequest;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDataset;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetContentClassification;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.io.IOException;
import java.time.Clock;
import java.util.Objects;

/**
 * 受令牌权限约束登记私有合成评测数据集，并在元数据提交失败时删除已写对象。
 */
public final class EvaluationDatasetRegistrationService {

    private static final PermissionCode ASSISTANT_WRITE = new PermissionCode("assistant:write");

    private final EvaluationDatasetRegistrationStore datasets;
    private final EvaluationDatasetPrivateContentStore contents;
    private final EvaluationDatasetContentStagingPort staging;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    public EvaluationDatasetRegistrationService(
            EvaluationDatasetRegistrationStore datasets,
            EvaluationDatasetPrivateContentStore contents,
            EvaluationDatasetContentStagingPort staging,
            EntityIdGenerator identifiers,
            Clock clock) {
        this.datasets = Objects.requireNonNull(datasets, "评测数据集存储不能为空");
        this.contents = Objects.requireNonNull(contents, "评测私有内容存储不能为空");
        this.staging = Objects.requireNonNull(staging, "评测内容暂存不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 登记并发布首批合成数据集清单。
     *
     * @throws IllegalArgumentException 当当前令牌不含 `assistant:write` 时拒绝。
     */
    public EvaluationDataset register(
            TrustedRequestContext context,
            EvaluationDatasetRegistrationRequest request) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        request = Objects.requireNonNull(request, "评测数据集登记请求不能为空");
        if (!context.hasPermission(ASSISTANT_WRITE)) {
            throw new IllegalArgumentException("当前主体缺少助手写权限");
        }
        try (var staged = staging.stage(request.content());
                var input = staged.openStream()) {
            return registerStaged(
                    context,
                    new EvaluationDatasetRegistrationContent(
                            input,
                            staged.contentDigest(),
                            staged.entryCount(),
                            request.retentionPolicyId()));
        } catch (IOException exception) {
            throw new IllegalStateException("评测私有内容处理失败", exception);
        }
    }

    private EvaluationDataset registerStaged(
            TrustedRequestContext context,
            EvaluationDatasetRegistrationContent content) {
        var id = new EvaluationDatasetId(identifiers.nextId());
        var reference = contents.put(context.tenantId(), id.value(), content);
        try {
            var dataset = EvaluationDataset.create(
                            context.tenantId(), id, reference, content.contentDigest(), content.entryCount(),
                            EvaluationDatasetContentClassification.SYNTHETIC, content.retentionPolicyId(), clock.instant())
                    .publish();
            datasets.commit(dataset);
            return dataset;
        } catch (RuntimeException exception) {
            contents.delete(reference);
            throw exception;
        }
    }
}

package com.aiplatform.appstudio.presentation.messaging.consumer;

import com.aiplatform.appstudio.domain.port.modelpolicy.AppModelUsagePolicyProjectionRepository;
import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyEventReceiptRepository;
import com.aiplatform.appstudio.infrastructure.messaging.converter.ModelUsagePolicyPublishedMessageConverter;
import com.aiplatform.appstudio.infrastructure.messaging.integrity.ModelUsagePolicyPayloadIntegrityVerifier;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模型使用策略发布事件消费者，维护 app-studio 本地模型治理投影。
 */
@Component("appStudioModelUsagePolicyPublished")
@ConditionalOnProperty(name = "spring.cloud.stream.bindings.appStudioModelUsagePolicyPublished-in-0.destination")
@Transactional
public class AppStudioModelUsagePolicyPublishedConsumer implements Consumer<DomainEventMessage> {
    private static final String EVENT_TYPE = "ModelUsagePolicyPublished";
    private static final String AGGREGATE_TYPE = "ModelConfig";
    private static final String PRODUCER = "model-hub";
    private static final long EVENT_VERSION = 2;
    private static final String SCHEMA_VERSION = "schema/model-usage-policy.v2";

    private final AppModelUsagePolicyProjectionRepository repository;
    private final ModelUsagePolicyEventReceiptRepository receipts;
    private final ModelUsagePolicyPublishedMessageConverter converter;
    private final ModelUsagePolicyPayloadIntegrityVerifier integrityVerifier;

    public AppStudioModelUsagePolicyPublishedConsumer(
            AppModelUsagePolicyProjectionRepository repository,
            ModelUsagePolicyEventReceiptRepository receipts,
            ModelUsagePolicyPublishedMessageConverter converter,
            ModelUsagePolicyPayloadIntegrityVerifier integrityVerifier
    ) {
        this.repository = Objects.requireNonNull(repository, "模型策略投影仓储不能为空");
        this.receipts = Objects.requireNonNull(receipts, "模型策略事件消费凭据仓储不能为空");
        this.converter = Objects.requireNonNull(converter, "模型策略事件转换器不能为空");
        this.integrityVerifier = Objects.requireNonNull(integrityVerifier, "模型策略载荷完整性校验器不能为空");
    }

    @Override
    public void accept(DomainEventMessage message) {
        onMessage(message);
    }

    public void onMessage(DomainEventMessage message) {
        Objects.requireNonNull(message, "领域事件不能为空");
        if (!EVENT_TYPE.equals(message.eventType())) {
            return;
        }
        validateEnvelope(message);
        integrityVerifier.verify(message.payloadJson());
        if (!receipts.recordIfAbsent(message.messageId(), message.version(), message.occurredAt())) {
            return;
        }
        var projection = converter.toProjection(message);
        if (!message.aggregateId().equals(projection.modelConfigId())) {
            throw new IllegalArgumentException("模型策略事件聚合 ID 与载荷模型配置 ID 不一致");
        }
        repository.save(projection);
    }

    private static void validateEnvelope(DomainEventMessage message) {
        if (message.version() != EVENT_VERSION || !SCHEMA_VERSION.equals(message.schemaVersion())) {
            throw new IllegalArgumentException("模型策略事件协议版本不受支持");
        }
        if (!AGGREGATE_TYPE.equals(message.aggregateType())) {
            throw new IllegalArgumentException("模型策略事件聚合类型不受支持");
        }
        if (!PRODUCER.equals(message.producer())) {
            throw new IllegalArgumentException("模型策略事件生产者不受信任");
        }
    }
}

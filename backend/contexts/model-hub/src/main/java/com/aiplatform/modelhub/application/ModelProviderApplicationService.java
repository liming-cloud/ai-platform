package com.aiplatform.modelhub.application;

import com.aiplatform.modelhub.application.command.CreateModelProviderCommand;
import com.aiplatform.modelhub.application.command.DisableModelProviderCommand;
import com.aiplatform.modelhub.application.error.ModelHubErrorCodes;
import com.aiplatform.modelhub.application.query.ModelProviderQueryRepository;
import com.aiplatform.modelhub.application.result.CreateModelProviderResult;
import com.aiplatform.modelhub.application.result.ModelProviderDetailResult;
import com.aiplatform.modelhub.domain.ModelApprovalCancellation;
import com.aiplatform.modelhub.domain.ModelConfigChange;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderAuthTypes;
import com.aiplatform.modelhub.domain.ModelProviderCreation;
import com.aiplatform.modelhub.domain.ModelProviderCreationService;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.port.ModelConfigApprovalPort;
import com.aiplatform.modelhub.domain.port.ModelConfigRepository;
import com.aiplatform.modelhub.domain.port.ModelProviderRepository;
import com.aiplatform.modelhub.domain.port.ModelSecretVaultPort;
import com.aiplatform.modelhub.domain.port.ModelUsagePolicyPublisher;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.net.OutboundEndpointPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模型供应商应用服务，编排供应商持久化、凭证材料保管、审批事件请求和审计事件。
 */
@Service
public class ModelProviderApplicationService {
    private final ModelProviderRepository providers;
    private final ModelProviderQueryRepository providerQueries;
    private final ModelSecretVaultPort secretVault;
    private final ModelConfigApprovalPort approvals;
    private final ModelProviderCreationService creationService;
    private final ModelConfigRepository configs;
    private final ModelUsagePolicyPublisher usagePolicyPublisher;
    private final AuditEventPublisher auditPublisher;
    private final ModelProviderConnectionContract connectionContract;
    private final Clock clock;

    @Autowired
    public ModelProviderApplicationService(
            ModelProviderRepository providers,
            ModelProviderQueryRepository providerQueries,
            ModelSecretVaultPort secretVault,
            ModelConfigApprovalPort approvals,
            ModelProviderCreationService creationService,
            ModelConfigRepository configs,
            ModelUsagePolicyPublisher usagePolicyPublisher,
            AuditEventPublisher auditPublisher,
            OutboundEndpointPolicy outboundEndpointPolicy,
            Clock clock,
            @Value("${aip.model-hub.local-deterministic-enabled:false}") boolean localDeterministicEnabled
    ) {
        this.providers = Objects.requireNonNull(providers, "模型供应商仓储不能为空");
        this.providerQueries = Objects.requireNonNull(providerQueries, "模型供应商查询仓储不能为空");
        this.secretVault = Objects.requireNonNull(secretVault, "模型供应商凭证材料保管端口不能为空");
        this.approvals = Objects.requireNonNull(approvals, "模型配置审批端口不能为空");
        this.creationService = Objects.requireNonNull(creationService, "模型供应商创建领域服务不能为空");
        this.configs = Objects.requireNonNull(configs, "模型配置仓储不能为空");
        this.usagePolicyPublisher = Objects.requireNonNull(usagePolicyPublisher, "模型使用策略发布端口不能为空");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "审计发布器不能为空");
        this.connectionContract = new ModelProviderConnectionContract(
                outboundEndpointPolicy,
                localDeterministicEnabled
        );
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    ModelProviderApplicationService(
            ModelProviderRepository providers,
            ModelProviderQueryRepository providerQueries,
            ModelSecretVaultPort secretVault,
            ModelConfigApprovalPort approvals,
            ModelProviderCreationService creationService,
            ModelConfigRepository configs,
            ModelUsagePolicyPublisher usagePolicyPublisher,
            AuditEventPublisher auditPublisher,
            OutboundEndpointPolicy outboundEndpointPolicy,
            Clock clock
    ) {
        this(
                providers,
                providerQueries,
                secretVault,
                approvals,
                creationService,
                configs,
                usagePolicyPublisher,
                auditPublisher,
                outboundEndpointPolicy,
                clock,
                true
        );
    }

    @Transactional
    public CreateModelProviderResult createProvider(CreateModelProviderCommand command) {
        Objects.requireNonNull(command, "创建模型供应商命令不能为空");
        String safeEndpoint = connectionContract.validateAndNormalize(
                command.providerType(),
                command.apiEndpoint(),
                command.authType()
        );
        Instant now = Instant.now(clock);
        String traceId = TraceContext.currentTraceId();
        String providerId = providers.nextProviderId();
        ModelProviderCreation creation = creationService.createProvider(
                command.tenantId(),
                providerId,
                command.providerType(),
                command.providerName(),
                safeEndpoint,
                command.authType(),
                command.operatorId(),
                traceId,
                now
        );
        ModelProvider provider = creation.provider();
        ModelConfigChange change = creation.configChange();
        providers.insert(provider);
        if (ModelProviderAuthTypes.requiresCredentialMaterial(provider.authType())) {
            secretVault.store(
                    command.tenantId(),
                    providerId,
                    command.credentialMaterialInput(),
                    command.operatorId()
            );
        }
        String approvalRequestId = approvals.requestApproval(change);
        requireExpectedApprovalRequestId(provider.approvalRequestId(), approvalRequestId);
        auditPublisher.publish(createAuditEvent(command, change, approvalRequestId, traceId, now));
        return new CreateModelProviderResult(providerId, provider.status().name(), approvalRequestId);
    }

    @Transactional(readOnly = true)
    public List<ModelProviderDetailResult> listProviders(com.aiplatform.shared.kernel.TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        return providerQueries.findAllDetails(tenantId);
    }

    @Transactional
    public ModelProviderDetailResult disableProvider(DisableModelProviderCommand command) {
        Objects.requireNonNull(command, "停用模型供应商命令不能为空");
        ModelProvider provider = providers.findById(command.tenantId(), command.providerId())
                .orElseThrow(() -> new ApplicationException(
                        ModelHubErrorCodes.PROVIDER_NOT_FOUND,
                        "供应商 ID=" + command.providerId()
                ));
        requireExpectedVersion(provider.version(), command.expectedVersion());
        boolean cancelsPendingApproval = provider.status() == ModelProviderStatus.PENDING_APPROVAL;
        Instant now = Instant.now(clock);
        String traceId = TraceContext.currentTraceId();
        ModelProvider disabled = providers.compareAndSet(
                        provider.disable(now),
                        command.expectedVersion(),
                        provider.status()
                )
                .orElseThrow(ModelProviderApplicationService::versionConflict);
        if (cancelsPendingApproval) {
            approvals.cancelApproval(new ModelApprovalCancellation(
                    command.tenantId(),
                    provider.approvalRequestId(),
                    ModelConfigChange.RESOURCE_TYPE_MODEL_PROVIDER,
                    provider.providerId(),
                    command.operatorId(),
                    command.reason(),
                    traceId,
                    now
            ));
        }
        configs.findByProviderId(command.tenantId(), command.providerId())
                .forEach(config -> usagePolicyPublisher.publish(disabled, config));
        auditPublisher.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                command.tenantId().value(),
                command.operatorId(),
                "MODEL_PROVIDER",
                disabled.providerId(),
                cancelsPendingApproval ? "MODEL_PROVIDER_APPROVAL_CANCELLATION_REQUESTED" : "MODEL_PROVIDER_DISABLE",
                "HIGH",
                cancelsPendingApproval ? provider.approvalRequestId() : null,
                traceId,
                now,
                cancelsPendingApproval
                        ? Map.of(
                                "reason", command.reason(),
                                "previousStatus", provider.status().name(),
                                "outcome", "APPROVAL_CANCELLATION_REQUESTED"
                        )
                        : Map.of("reason", command.reason())
        ));
        return ModelProviderDetailResult.from(disabled);
    }

    private static AuditEvent createAuditEvent(
            CreateModelProviderCommand command,
            ModelConfigChange change,
            String approvalRequestId,
            String traceId,
            Instant now
    ) {
        return new AuditEvent(
                UUID.randomUUID().toString(),
                command.tenantId().value(),
                command.operatorId(),
                change.auditResourceType(),
                change.providerId(),
                change.auditAction(),
                change.riskLevel().auditValue(),
                approvalRequestId,
                traceId,
                now
        );
    }

    private static void requireExpectedApprovalRequestId(String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException(
                    "审批请求发布器返回了不一致的 requestId：expected=" + expected + "，actual=" + actual
            );
        }
    }

    private static void requireExpectedVersion(int actualVersion, int expectedVersion) {
        if (expectedVersion < 0 || actualVersion != expectedVersion) {
            throw versionConflict();
        }
    }

    private static ApplicationException versionConflict() {
        return new ApplicationException(
                ModelHubErrorCodes.VERSION_CONFLICT,
                "模型供应商已被其他操作修改，expectedVersion 不再有效"
        );
    }
}

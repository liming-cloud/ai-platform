package com.aiplatform.modelhub.application;

import com.aiplatform.modelhub.application.command.CreateModelConfigCommand;
import com.aiplatform.modelhub.application.command.DisableModelConfigCommand;
import com.aiplatform.modelhub.application.command.UpdateModelConfigCommand;
import com.aiplatform.modelhub.application.error.ModelHubErrorCodes;
import com.aiplatform.modelhub.application.query.ModelConfigQueryRepository;
import com.aiplatform.modelhub.application.result.CreateModelConfigResult;
import com.aiplatform.modelhub.application.result.ModelConfigDetailResult;
import com.aiplatform.modelhub.application.result.ModelConnectivityTestResult;
import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigChange;
import com.aiplatform.modelhub.domain.ModelConfigCreationService;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderKinds;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelRuntimeConnectivity;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.modelhub.domain.ModelRuntimeValidation;
import com.aiplatform.modelhub.domain.ModelType;
import com.aiplatform.modelhub.domain.port.ModelConfigApprovalPort;
import com.aiplatform.modelhub.domain.port.ModelConfigRepository;
import com.aiplatform.modelhub.domain.port.ModelProviderRepository;
import com.aiplatform.modelhub.domain.port.ModelRuntimeOptionsPort;
import com.aiplatform.modelhub.domain.port.ModelUsagePolicyPublisher;
import com.aiplatform.modelhub.domain.service.ModelConfigActivationPolicy;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.observability.TraceContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 模型配置应用服务，编排供应商归属校验、options 校验、持久化和审批/连通性状态回写。
 */
@Service
public class ModelConfigApplicationService {
    private final ModelProviderRepository providers;
    private final ModelConfigRepository configs;
    private final ModelConfigQueryRepository configQueries;
    private final ModelRuntimeOptionsPort runtimeOptions;
    private final ModelConnectivityProbe connectivityProbe;
    private final ModelConfigApprovalPort approvals;
    private final ModelConfigCreationService creationService;
    private final ModelConfigActivationPolicy activationPolicy;
    private final ModelUsagePolicyPublisher usagePolicyPublisher;
    private final AuditEventPublisher auditPublisher;
    private final Clock clock;
    private final boolean localDeterministicEnabled;
    private final TransactionOperations transactions;

    @Autowired
    public ModelConfigApplicationService(
            ModelProviderRepository providers,
            ModelConfigRepository configs,
            ModelConfigQueryRepository configQueries,
            ModelRuntimeOptionsPort runtimeOptions,
            ModelConnectivityProbe connectivityProbe,
            ModelConfigApprovalPort approvals,
            ModelConfigCreationService creationService,
            ModelConfigActivationPolicy activationPolicy,
            ModelUsagePolicyPublisher usagePolicyPublisher,
            AuditEventPublisher auditPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${aip.model-hub.local-deterministic-enabled:false}") boolean localDeterministicEnabled
    ) {
        this(
                providers,
                configs,
                configQueries,
                runtimeOptions,
                connectivityProbe,
                approvals,
                creationService,
                activationPolicy,
                usagePolicyPublisher,
                auditPublisher,
                clock,
                localDeterministicEnabled,
                transactionTemplate(transactionManager)
        );
    }

    ModelConfigApplicationService(
            ModelProviderRepository providers,
            ModelConfigRepository configs,
            ModelConfigQueryRepository configQueries,
            ModelRuntimeOptionsPort runtimeOptions,
            ModelConnectivityProbe connectivityProbe,
            ModelConfigApprovalPort approvals,
            ModelConfigCreationService creationService,
            ModelConfigActivationPolicy activationPolicy,
            ModelUsagePolicyPublisher usagePolicyPublisher,
            AuditEventPublisher auditPublisher,
            Clock clock,
            boolean localDeterministicEnabled,
            TransactionOperations transactions
    ) {
        this.providers = Objects.requireNonNull(providers, "模型供应商仓储不能为空");
        this.configs = Objects.requireNonNull(configs, "模型配置仓储不能为空");
        this.configQueries = Objects.requireNonNull(configQueries, "模型配置查询仓储不能为空");
        this.runtimeOptions = Objects.requireNonNull(runtimeOptions, "模型运行时 options 端口不能为空");
        this.connectivityProbe = Objects.requireNonNull(connectivityProbe, "模型连通性探测器不能为空");
        this.approvals = Objects.requireNonNull(approvals, "模型审批端口不能为空");
        this.creationService = Objects.requireNonNull(creationService, "模型配置创建领域服务不能为空");
        this.activationPolicy = Objects.requireNonNull(activationPolicy, "模型配置启用策略不能为空");
        this.usagePolicyPublisher = Objects.requireNonNull(usagePolicyPublisher, "模型使用策略发布端口不能为空");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "审计发布器不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
        this.localDeterministicEnabled = localDeterministicEnabled;
        this.transactions = Objects.requireNonNull(transactions, "模型配置事务执行器不能为空");
    }

    ModelConfigApplicationService(
            ModelProviderRepository providers,
            ModelConfigRepository configs,
            ModelConfigQueryRepository configQueries,
            ModelRuntimeOptionsPort runtimeOptions,
            ModelConfigApprovalPort approvals,
            ModelConfigCreationService creationService,
            ModelConfigActivationPolicy activationPolicy,
            ModelUsagePolicyPublisher usagePolicyPublisher,
            AuditEventPublisher auditPublisher,
            Clock clock
    ) {
        this(
                providers,
                configs,
                configQueries,
                runtimeOptions,
                runtimeOptions::testConnectivity,
                approvals,
                creationService,
                activationPolicy,
                usagePolicyPublisher,
                auditPublisher,
                clock,
                true,
                directTransactions()
        );
    }

    ModelConfigApplicationService(
            ModelProviderRepository providers,
            ModelConfigRepository configs,
            ModelConfigQueryRepository configQueries,
            ModelRuntimeOptionsPort runtimeOptions,
            ModelConfigApprovalPort approvals,
            ModelConfigCreationService creationService,
            ModelConfigActivationPolicy activationPolicy,
            ModelUsagePolicyPublisher usagePolicyPublisher,
            AuditEventPublisher auditPublisher,
            Clock clock,
            boolean localDeterministicEnabled
    ) {
        this(
                providers,
                configs,
                configQueries,
                runtimeOptions,
                runtimeOptions::testConnectivity,
                approvals,
                creationService,
                activationPolicy,
                usagePolicyPublisher,
                auditPublisher,
                clock,
                localDeterministicEnabled,
                directTransactions()
        );
    }

    @Transactional
    public CreateModelConfigResult createModelConfig(CreateModelConfigCommand command) {
        Objects.requireNonNull(command, "创建模型配置命令不能为空");
        ModelProvider provider = providers.findById(command.tenantId(), command.providerId())
                .orElseThrow(() -> new ApplicationException(
                        ModelHubErrorCodes.PROVIDER_NOT_FOUND,
                        "供应商 ID=" + command.providerId()
                ));
        requireProviderCapabilityEnabled(provider);
        requireActiveProviderForModelChange(provider);
        requireCompatibleRuntimeProviderKind(provider, command.options());
        configs.findByProviderAndCode(command.tenantId(), command.providerId(), command.modelCode())
                .ifPresent(existing -> {
                    throw new ApplicationException(
                            ModelHubErrorCodes.MODEL_CONFIG_DUPLICATED,
                            "供应商 ID=" + command.providerId() + "，模型编码=" + existing.modelCode()
                    );
        });
        Instant now = Instant.now(clock);
        String modelConfigId = configs.nextModelConfigId();
        String approvalRequestId = provider.approvalRequiredForConfigChange()
                ? UUID.randomUUID().toString()
                : null;
        ModelConfig config = createDomainConfig(
                command,
                provider,
                modelConfigId,
                approvalRequestId,
                now
        );
        ModelRuntimeValidation validation = runtimeOptions.validate(provider, config);
        if (!validation.valid()) {
            throw new ApplicationException(
                    ModelHubErrorCodes.INVALID_MODEL_CONFIG,
                    "options 校验失败：" + validation.summary()
            );
        }
        ModelConfigChange approvalChange = approvalRequestId == null
                ? null
                : ModelConfigChange.forModelConfigCreation(
                        provider,
                        config,
                        command.operatorId(),
                        generatedTraceId(modelConfigId),
                        now
        );
        config = bindApprovalEvidence(config, approvalChange);
        ModelConfig saved = configs.insert(config);
        String requestedApprovalId = requestApproval(approvalChange);
        publishUsagePolicy(provider, saved);
        audit(command.tenantId().value(), command.operatorId(), saved.modelConfigId(),
                "MODEL_CONFIG_CREATE", requestedApprovalId, now,
                Map.of(
                        "providerId", saved.providerId(),
                        "modelCode", saved.modelCode(),
                        "modelName", saved.modelName(),
                        "modelType", saved.modelType().name(),
                        "status", saved.status().name(),
                        "connectivityStatus", saved.connectivityStatus().name()
                ));
        return CreateModelConfigResult.from(saved);
    }

    @Transactional
    public ModelConfigDetailResult updateModelConfig(UpdateModelConfigCommand command) {
        Objects.requireNonNull(command, "更新模型配置命令不能为空");
        ModelProvider provider = requireProvider(command.tenantId(), command.providerId());
        requireProviderCapabilityEnabled(provider);
        requireActiveProviderForModelChange(provider);
        ModelConfig existing = requireModelConfig(command.tenantId(), command.providerId(), command.modelConfigId());
        requireExpectedVersion(existing.version(), command.expectedVersion());
        requireModelConfigUpdateAllowed(existing);
        requireCompatibleRuntimeProviderKind(provider, command.options());
        configs.findByProviderAndCode(command.tenantId(), command.providerId(), command.modelCode())
                .filter(found -> !found.modelConfigId().equals(command.modelConfigId()))
                .ifPresent(found -> {
                    throw new ApplicationException(
                            ModelHubErrorCodes.MODEL_CONFIG_DUPLICATED,
                            "供应商 ID=" + command.providerId() + "，模型编码=" + found.modelCode()
                    );
        });
        Instant now = Instant.now(clock);
        String nextApprovalRequestId = nextStatusAfterUpdate(provider) == ModelConfigStatus.PENDING_APPROVAL
                ? UUID.randomUUID().toString()
                : existing.approvalRequestId();
        ModelConfig updated = updateDomainConfig(
                command,
                provider,
                existing,
                nextApprovalRequestId,
                now
        );
        ModelRuntimeValidation validation = runtimeOptions.validate(provider, updated);
        if (!validation.valid()) {
            throw new ApplicationException(
                    ModelHubErrorCodes.INVALID_MODEL_CONFIG,
                    "options 校验失败：" + validation.summary()
            );
        }
        ModelConfigChange approvalChange = updated.status() == ModelConfigStatus.PENDING_APPROVAL
                ? ModelConfigChange.forModelConfigUpdate(
                        provider,
                        existing,
                        updated,
                        command.operatorId(),
                        generatedTraceId(existing.modelConfigId()),
                        now
                )
                : null;
        updated = bindApprovalEvidence(updated, approvalChange);
        ModelConfig saved = configs.compareAndSet(updated, command.expectedVersion(), existing.status())
                .orElseThrow(ModelConfigApplicationService::versionConflict);
        String approvalRequestId = requestApproval(approvalChange);
        publishUsagePolicy(provider, saved);
        audit(command.tenantId().value(), command.operatorId(), saved.modelConfigId(),
                "MODEL_CONFIG_UPDATE", approvalRequestId, now,
                Map.of("modelName", saved.modelName(), "modelType", saved.modelType().name()));
        return ModelConfigDetailResult.from(saved);
    }

    @Transactional
    public ModelConfigDetailResult disableModelConfig(DisableModelConfigCommand command) {
        Objects.requireNonNull(command, "停用模型配置命令不能为空");
        ModelProvider provider = requireProvider(command.tenantId(), command.providerId());
        ModelConfig config = requireModelConfig(command.tenantId(), command.providerId(), command.modelConfigId());
        requireExpectedVersion(config.version(), command.expectedVersion());
        requireModelConfigDisableAllowed(config);
        Instant now = Instant.now(clock);
        ModelConfig disabled = configs.compareAndSet(
                        config.deprecate(now),
                        command.expectedVersion(),
                        config.status()
                )
                .orElseThrow(ModelConfigApplicationService::versionConflict);
        publishUsagePolicy(provider, disabled);
        audit(command.tenantId().value(), command.operatorId(), disabled.modelConfigId(),
                "MODEL_CONFIG_DISABLE", null, now,
                Map.of(
                        "modelName", disabled.modelName(),
                        "modelType", disabled.modelType().name(),
                        "reason", command.reason()
                ));
        return ModelConfigDetailResult.from(disabled);
    }

    public ModelConnectivityTestResult testConnectivity(
            com.aiplatform.shared.kernel.TenantId tenantId,
            String providerId,
            String modelConfigId,
            int expectedVersion
    ) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        ModelConnectivityProbeSnapshot snapshot = requireTransactionResult(transactions.execute(status ->
                prepareProbe(tenantId, providerId, modelConfigId, expectedVersion)
        ));
        ModelRuntimeConnectivity connectivity = connectivityProbe.test(snapshot.provider(), snapshot.config());
        return requireTransactionResult(transactions.execute(status -> completeProbe(snapshot, connectivity)));
    }

    @Transactional(readOnly = true)
    public List<ModelConfigDetailResult> listModelConfigs(
            com.aiplatform.shared.kernel.TenantId tenantId,
            String providerId
    ) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        return configQueries.findDetailsByProviderId(tenantId, providerId);
    }

    private ModelConnectivityProbeSnapshot prepareProbe(
            com.aiplatform.shared.kernel.TenantId tenantId,
            String providerId,
            String modelConfigId,
            int expectedVersion
    ) {
        ModelProvider provider = requireProvider(tenantId, providerId);
        requireProviderCapabilityEnabled(provider);
        ModelConfig config = requireModelConfig(tenantId, providerId, modelConfigId);
        requireExpectedVersion(config.version(), expectedVersion);
        requireConnectivityTestAllowed(provider, config);
        return new ModelConnectivityProbeSnapshot(provider, config);
    }

    private ModelConnectivityTestResult completeProbe(
            ModelConnectivityProbeSnapshot snapshot,
            ModelRuntimeConnectivity connectivity
    ) {
        ModelProvider provider = requireProvider(
                snapshot.provider().tenantId(),
                snapshot.provider().providerId()
        );
        ModelConfig config = requireModelConfig(
                snapshot.config().tenantId(),
                snapshot.config().providerId(),
                snapshot.config().modelConfigId()
        );
        if (provider.version() != snapshot.provider().version()
                || provider.status() != snapshot.provider().status()
                || config.version() != snapshot.config().version()
                || config.status() != snapshot.config().status()) {
            throw versionConflict();
        }
        requireProviderCapabilityEnabled(provider);
        requireConnectivityTestAllowed(provider, config);
        Instant now = Instant.now(clock);
        ModelConfig checked = config.withConnectivity(connectivity, now, now);
        ModelConfig updated = activationPolicy.activateWhenReady(provider, checked, now);
        ModelConfig saved = configs.compareAndSet(updated, config.version(), config.status())
                .orElseThrow(ModelConfigApplicationService::versionConflict);
        publishUsagePolicy(provider, saved);
        return ModelConnectivityTestResult.from(saved, connectivity.connected());
    }

    private ModelConfig createDomainConfig(
            CreateModelConfigCommand command,
            ModelProvider provider,
            String modelConfigId,
            String approvalRequestId,
            Instant now
    ) {
        try {
            return creationService.createModelConfig(
                    command.tenantId(),
                    modelConfigId,
                    provider,
                    command.modelCode(),
                    command.modelName(),
                    command.modelType(),
                    command.contextWindow(),
                    command.embeddingDimension(),
                    command.inputPricePer1k(),
                    command.outputPricePer1k(),
                    command.options(),
                    approvalRequestId,
                    now
            );
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(
                    ModelHubErrorCodes.INVALID_MODEL_CONFIG,
                    exception.getMessage()
            );
        }
    }

    private ModelProvider requireProvider(com.aiplatform.shared.kernel.TenantId tenantId, String providerId) {
        return providers.findById(tenantId, providerId)
                .orElseThrow(() -> new ApplicationException(
                        ModelHubErrorCodes.PROVIDER_NOT_FOUND,
                        "供应商 ID=" + providerId
                ));
    }

    private ModelConfig requireModelConfig(
            com.aiplatform.shared.kernel.TenantId tenantId,
            String providerId,
            String modelConfigId
    ) {
        return configs.findById(tenantId, providerId, modelConfigId)
                .orElseThrow(() -> new ApplicationException(
                        ModelHubErrorCodes.MODEL_CONFIG_NOT_FOUND,
                        "供应商 ID=" + providerId + "，模型配置 ID=" + modelConfigId
                ));
    }

    private ModelConfig updateDomainConfig(
            UpdateModelConfigCommand command,
            ModelProvider provider,
            ModelConfig existing,
            String approvalRequestId,
            Instant now
    ) {
        try {
            return existing.withRuntimeSettings(
                    command.modelCode(),
                    command.modelName(),
                    parseModelType(command.modelType()),
                    command.contextWindow(),
                    command.embeddingDimension(),
                    command.inputPricePer1k(),
                    command.outputPricePer1k(),
                    new ModelRuntimeOptions(command.options()),
                    nextStatusAfterUpdate(provider),
                    approvalRequestId,
                    now
            );
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(
                    ModelHubErrorCodes.INVALID_MODEL_CONFIG,
                    exception.getMessage()
            );
        }
    }

    private static ModelConfigStatus nextStatusAfterUpdate(ModelProvider provider) {
        if (provider.status() != ModelProviderStatus.ACTIVE || provider.approvalRequiredForConfigChange()) {
            return ModelConfigStatus.PENDING_APPROVAL;
        }
        return ModelConfigStatus.INACTIVE;
    }

    private static ModelType parseModelType(String modelType) {
        if (modelType == null || modelType.isBlank()) {
            throw new IllegalArgumentException("模型类型不能为空");
        }
        try {
            return ModelType.valueOf(modelType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("模型类型不支持：" + modelType, exception);
        }
    }

    private String requestApproval(ModelConfigChange change) {
        if (change == null || !change.requiresApproval()) {
            return null;
        }
        String approvalRequestId = approvals.requestApproval(change);
        if (!Objects.equals(change.changeId(), approvalRequestId)) {
            throw new IllegalStateException(
                    "审批请求发布器返回了不一致的 requestId：expected="
                            + change.changeId() + "，actual=" + approvalRequestId
            );
        }
        return approvalRequestId;
    }

    private static ModelConfig bindApprovalEvidence(ModelConfig config, ModelConfigChange change) {
        if (change == null) {
            return config;
        }
        return config.withApprovalEvidence(change.approvalEvidence(), change.targetDigest());
    }

    private static String generatedTraceId(String modelConfigId) {
        return "MODEL-CONFIG-APPROVAL-" + modelConfigId + "-" + UUID.randomUUID();
    }

    private static void requireCompatibleRuntimeProviderKind(
            ModelProvider provider,
            Map<String, Object> options
    ) {
        try {
            ModelRuntimeOptions runtimeOptions = new ModelRuntimeOptions(options);
            ModelProviderKinds.requireCompatibleRuntimeKind(provider.providerCode(), runtimeOptions.providerKind());
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(ModelHubErrorCodes.INVALID_MODEL_CONFIG, exception.getMessage());
        }
    }

    private static void requireActiveProviderForModelChange(ModelProvider provider) {
        if (provider.status() != ModelProviderStatus.ACTIVE) {
            throw new ApplicationException(
                    ModelHubErrorCodes.INVALID_MODEL_CONFIG,
                    "模型供应商审批通过后才可创建或更新模型配置"
            );
        }
    }

    private void requireProviderCapabilityEnabled(ModelProvider provider) {
        if (!localDeterministicEnabled
                && ModelProviderKinds.LOCAL_DETERMINISTIC.equalsIgnoreCase(provider.providerCode())) {
            throw new ApplicationException(
                    ModelHubErrorCodes.INVALID_MODEL_CONFIG,
                    "本地确定性运行时能力未启用，禁止管理其模型配置或执行连通性测试"
            );
        }
    }

    private static void requireModelConfigUpdateAllowed(ModelConfig config) {
        if (config.status() == ModelConfigStatus.PENDING_APPROVAL) {
            throw new ApplicationException(
                    ModelHubErrorCodes.INVALID_MODEL_CONFIG,
                    "模型配置正在待审批，审批完成前不可重复更新"
            );
        }
        if (config.status() == ModelConfigStatus.DEPRECATED) {
            throw new ApplicationException(
                    ModelHubErrorCodes.INVALID_MODEL_CONFIG,
                    "已停用的模型配置不可更新，请创建新的模型配置"
            );
        }
    }

    private static void requireModelConfigDisableAllowed(ModelConfig config) {
        if (config.status() == ModelConfigStatus.PENDING_APPROVAL) {
            throw new ApplicationException(
                    ModelHubErrorCodes.INVALID_MODEL_CONFIG,
                    "待审批模型配置不可停用，请先完成或撤销审批"
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
                "模型配置已被其他操作修改，expectedVersion 不再有效"
        );
    }

    private static TransactionOperations transactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空")
        );
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private static TransactionOperations directTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return Objects.requireNonNull(action, "事务回调不能为空").doInTransaction(null);
            }
        };
    }

    private static <T> T requireTransactionResult(T value) {
        return Objects.requireNonNull(value, "事务执行结果不能为空");
    }

    private static void requireConnectivityTestAllowed(ModelProvider provider, ModelConfig config) {
        if (provider.status() != ModelProviderStatus.ACTIVE) {
            throw new ApplicationException(
                    ModelHubErrorCodes.INVALID_MODEL_CONFIG,
                    "模型供应商审批通过且处于启用状态后才可执行连通性测试"
            );
        }
        if (config.status() != ModelConfigStatus.INACTIVE && config.status() != ModelConfigStatus.ACTIVE) {
            throw new ApplicationException(
                    ModelHubErrorCodes.INVALID_MODEL_CONFIG,
                    "模型配置审批通过后才可执行连通性测试"
            );
        }
    }

    private void publishUsagePolicy(ModelProvider provider, ModelConfig config) {
        usagePolicyPublisher.publish(provider, config);
    }

    private void audit(
            String tenantId,
            String operatorId,
            String modelConfigId,
            String action,
            String approvalRequestId,
            Instant now,
            Map<String, Object> details
    ) {
        auditPublisher.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                tenantId,
                operatorId,
                "MODEL_CONFIG",
                modelConfigId,
                action,
                "HIGH",
                approvalRequestId,
                TraceContext.currentTraceId(),
                now,
                details
        ));
    }
}

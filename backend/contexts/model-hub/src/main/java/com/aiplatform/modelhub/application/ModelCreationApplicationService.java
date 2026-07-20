package com.aiplatform.modelhub.application;

import com.aiplatform.modelhub.application.command.CreateModelConfigCommand;
import com.aiplatform.modelhub.application.command.CreateModelProviderCommand;
import com.aiplatform.modelhub.application.error.ModelHubErrorCodes;
import com.aiplatform.modelhub.application.idempotency.ModelCreationCommandDigest;
import com.aiplatform.modelhub.application.idempotency.ModelCreationRequestClaim;
import com.aiplatform.modelhub.application.idempotency.ModelCreationRequestRepository;
import com.aiplatform.modelhub.application.idempotency.ModelCreationResourceType;
import com.aiplatform.modelhub.application.idempotency.ModelCreationResultSnapshot;
import com.aiplatform.modelhub.application.result.CreateModelConfigResult;
import com.aiplatform.modelhub.application.result.CreateModelProviderResult;
import com.aiplatform.modelhub.domain.ModelProviderKinds;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.Sha256Digests;
import com.aiplatform.shared.security.net.OutboundEndpointPolicy;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public create seam for ModelHub. It atomically binds a client request ID to one canonical command and resource.
 */
@Service
public class ModelCreationApplicationService {
    private final ModelProviderApplicationService providerService;
    private final ModelConfigApplicationService configService;
    private final ModelCreationRequestRepository requests;
    private final ModelProviderConnectionContract connectionContract;
    private final ModelCreationCommandDigest commandDigest;

    @Autowired
    public ModelCreationApplicationService(
            ModelProviderApplicationService providerService,
            ModelConfigApplicationService configService,
            ModelCreationRequestRepository requests,
            OutboundEndpointPolicy outboundEndpointPolicy,
            @Value("${aip.model-hub.local-deterministic-enabled:false}") boolean localDeterministicEnabled
    ) {
        this(
                providerService,
                configService,
                requests,
                new ModelProviderConnectionContract(outboundEndpointPolicy, localDeterministicEnabled),
                new ModelCreationCommandDigest()
        );
    }

    ModelCreationApplicationService(
            ModelProviderApplicationService providerService,
            ModelConfigApplicationService configService,
            ModelCreationRequestRepository requests,
            ModelProviderConnectionContract connectionContract,
            ModelCreationCommandDigest commandDigest
    ) {
        this.providerService = Objects.requireNonNull(providerService, "模型供应商应用服务不能为空");
        this.configService = Objects.requireNonNull(configService, "模型配置应用服务不能为空");
        this.requests = Objects.requireNonNull(requests, "模型创建幂等仓储不能为空");
        this.connectionContract = Objects.requireNonNull(connectionContract, "模型供应商连接契约不能为空");
        this.commandDigest = Objects.requireNonNull(commandDigest, "模型创建命令摘要器不能为空");
    }

    @Transactional
    public CreateModelProviderResult createProvider(CreateModelProviderCommand command) {
        Objects.requireNonNull(command, "创建模型供应商命令不能为空");
        String providerType = ModelProviderKinds.canonicalize(command.providerType());
        String endpoint = connectionContract.validateAndNormalize(
                providerType,
                command.apiEndpoint(),
                command.authType()
        );
        CreateModelProviderCommand normalized = new CreateModelProviderCommand(
                command.tenantId(),
                command.operatorId(),
                command.requestId(),
                providerType,
                command.providerName(),
                endpoint,
                command.authType(),
                command.credentialMaterialInput()
        );
        String digest = commandDigest.provider(normalized, endpoint);
        ModelCreationRequestClaim claim = claim(normalized, ModelCreationResourceType.MODEL_PROVIDER, digest);
        if (!claim.acquired()) {
            return claim.result().toProviderResult();
        }
        CreateModelProviderResult result = providerService.createProvider(normalized);
        requests.complete(
                normalized.tenantId(),
                ModelCreationResourceType.MODEL_PROVIDER,
                normalized.requestId(),
                digest,
                ModelCreationResultSnapshot.from(result)
        );
        return result;
    }

    @Transactional
    public CreateModelConfigResult createModelConfig(CreateModelConfigCommand command) {
        Objects.requireNonNull(command, "创建模型配置命令不能为空");
        String digest = commandDigest.modelConfig(command);
        ModelCreationRequestClaim claim = claim(command, ModelCreationResourceType.MODEL_CONFIG, digest);
        if (!claim.acquired()) {
            return claim.result().toModelConfigResult();
        }
        CreateModelConfigResult result = configService.createModelConfig(command);
        requests.complete(
                command.tenantId(),
                ModelCreationResourceType.MODEL_CONFIG,
                command.requestId(),
                digest,
                ModelCreationResultSnapshot.from(result)
        );
        return result;
    }

    private ModelCreationRequestClaim claim(
            CreateModelProviderCommand command,
            ModelCreationResourceType resourceType,
            String digest
    ) {
        return requireMatchingDigest(requests.claim(
                command.tenantId(), resourceType, command.requestId(), digest
        ), digest);
    }

    private ModelCreationRequestClaim claim(
            CreateModelConfigCommand command,
            ModelCreationResourceType resourceType,
            String digest
    ) {
        return requireMatchingDigest(requests.claim(
                command.tenantId(), resourceType, command.requestId(), digest
        ), digest);
    }

    private static ModelCreationRequestClaim requireMatchingDigest(
            ModelCreationRequestClaim claim,
            String expectedDigest
    ) {
        if (!Sha256Digests.constantTimeEquals(claim.requestDigest(), expectedDigest)) {
            throw new ApplicationException(
                    ModelHubErrorCodes.CREATION_IDEMPOTENCY_CONFLICT,
                    "同一 requestId 已绑定不同的规范化模型创建命令"
            );
        }
        return claim;
    }
}

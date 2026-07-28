package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller;

import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationAccessService;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrantId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.request.ModelConfigurationAccessGrantRequest;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.view.ModelConfigurationAccessGrantView;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型配置使用权授予与撤销入口。
 */
@RestController
@RequestMapping("/api/v1/model-configurations")
public class ModelConfigurationAccessController {

    private final ModelConfigurationAccessService access;

    public ModelConfigurationAccessController(ModelConfigurationAccessService access) {
        this.access = Objects.requireNonNull(access, "模型配置授权用例不能为空");
    }

    @PostMapping("/{modelConfigurationId}/grants")
    public ResponseEntity<ModelConfigurationAccessGrantView> grant(
            TrustedRequestContext context,
            @PathVariable long modelConfigurationId,
            @RequestBody ModelConfigurationAccessGrantRequest request) {
        var grant = access.grant(
                context, new ModelConfigurationId(modelConfigurationId), new TenantId(request.tenantId()),
                request.membershipId() == null ? null : new ModelConfigurationOwnerId(request.membershipId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ModelConfigurationAccessGrantView.from(grant));
    }

    @PostMapping("/grants/{grantId}/revoke")
    public ModelConfigurationAccessGrantView revoke(
            TrustedRequestContext context, @PathVariable long grantId) {
        return ModelConfigurationAccessGrantView.from(access.revoke(
                context, new ModelConfigurationAccessGrantId(grantId)));
    }
}

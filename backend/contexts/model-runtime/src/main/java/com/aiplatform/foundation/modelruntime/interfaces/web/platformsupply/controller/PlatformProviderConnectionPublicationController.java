package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.PublishPlatformProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderConnectionPublicationView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderConnectionPublicationService;
import com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request.PlatformProviderConnectionPublishRequest;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 连接独立发布入口；平台身份由正式会话解析，不接收租户或操作者字段。 */
@RestController
@RequestMapping("/api/v1/platform/model-supply/connections")
@ConditionalOnBean(PlatformProviderConnectionPublicationService.class)
public class PlatformProviderConnectionPublicationController {
    private final PlatformProviderConnectionPublicationService publications;

    public PlatformProviderConnectionPublicationController(PlatformProviderConnectionPublicationService publications) {
        this.publications = Objects.requireNonNull(publications, "发布用例不能为空");
    }

    /** 发布当前有效探测的连接修订，不产生任何租户模型。 */
    @PostMapping("/{providerConnectionId}/publish")
    public PlatformProviderConnectionPublicationView publish(PlatformRequestContext context,
            @PathVariable String providerConnectionId, @RequestBody PlatformProviderConnectionPublishRequest request) {
        return publications.publish(new PublishPlatformProviderConnectionCommand(
                context, providerConnectionId, request.expectedVersion()));
    }

    /** 当前修订未发布时显式返回 `published=false`，不借用历史版本。 */
    @GetMapping("/{providerConnectionId}/publication")
    public PlatformProviderConnectionPublicationView get(PlatformRequestContext context,
            @PathVariable String providerConnectionId, @RequestParam long expectedVersion) {
        return publications.get(context, providerConnectionId, expectedVersion);
    }
}

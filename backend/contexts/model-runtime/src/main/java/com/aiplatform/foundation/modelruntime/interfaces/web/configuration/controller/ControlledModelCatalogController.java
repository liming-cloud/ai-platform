package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.controller;

import com.aiplatform.foundation.modelruntime.application.configuration.service.ControlledModelCatalogService;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.request.ControlledModelCatalogEntryCreateFromDeploymentRequest;
import com.aiplatform.foundation.modelruntime.interfaces.web.configuration.view.ControlledModelCatalogEntryView;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.RequiresTenantFeature;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台管理员维护受控模型目录的 HTTP 入口。
 */
@RestController
@RequestMapping("/api/v1/controlled-model-catalog")
public class ControlledModelCatalogController {

    private final ControlledModelCatalogService catalog;

    public ControlledModelCatalogController(ControlledModelCatalogService catalog) {
        this.catalog = Objects.requireNonNull(catalog, "受控模型目录用例不能为空");
    }

    @PostMapping
    public ResponseEntity<ControlledModelCatalogEntryView> create(
            PlatformRequestContext context,
            @RequestBody ControlledModelCatalogEntryCreateFromDeploymentRequest request) {
        return ResponseEntity.ok(ControlledModelCatalogEntryView.from(
                catalog.createFromDeployment(context, new PlatformModelDeploymentId(request.parsedPlatformDeploymentId()),
                        request.expectedDeploymentVersion())));
    }

    @PostMapping("/{entryId}/publish")
    public ControlledModelCatalogEntryView publish(
            PlatformRequestContext context, @PathVariable long entryId) {
        return ControlledModelCatalogEntryView.from(catalog.publish(
                context, new ControlledModelCatalogEntryId(entryId)));
    }

    /**
     * 返回配置者可选择的已发布目录，不返回草稿、凭据或配置所有权信息。
     */
    @RequiresTenantFeature("modelGovernance")
    @GetMapping
    public PageResult<ControlledModelCatalogEntryView> list(
            TrustedRequestContext context,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        var query = new PageQuery(pageNumber, pageSize);
        var page = catalog.listPublished(context, query);
        return PageResult.of(
                page.items().stream()
                        .map(ControlledModelCatalogEntryView::from)
                        .toList(),
                query,
                page.totalElements());
    }
}

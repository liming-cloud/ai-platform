package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.RegisterObjectStorageDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.ProbeObjectStorageDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.synchronization.SynchronizeObjectStorageDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.publication.PublishObjectStorageSyncArtifactCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedObjectStorageDataSourceRegistrationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.ObjectStorageDataSourceProbeService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.ObjectStorageSynchronizationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.publication.ObjectStorageSyncArtifactPublicationService;
import com.aiplatform.foundation.security.identity.RequiresTenantFeature;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OBJECT_STORAGE 受治理来源登记入口；只接收平台签发的范围选择。 */
@RequiresTenantFeature("knowledgeManagement")
@RestController
@ConditionalOnBean({
        GovernedObjectStorageDataSourceRegistrationService.class,
        ObjectStorageDataSourceProbeService.class,
        ObjectStorageSynchronizationService.class,
        ObjectStorageSyncArtifactPublicationService.class
})
@RequestMapping("/api/v1/governed-data-sources/object-storage")
public class GovernedObjectStorageDataSourceController {
    private final GovernedObjectStorageDataSourceRegistrationService registration;
    private final ObjectStorageDataSourceProbeService probes;
    private final ObjectStorageSynchronizationService synchronizations;
    private final ObjectStorageSyncArtifactPublicationService publications;

    public GovernedObjectStorageDataSourceController(
            GovernedObjectStorageDataSourceRegistrationService registration,
            ObjectStorageDataSourceProbeService probes,
            ObjectStorageSynchronizationService synchronizations,
            ObjectStorageSyncArtifactPublicationService publications) {
        this.registration = Objects.requireNonNull(registration, "对象存储来源登记用例不能为空");
        this.probes = Objects.requireNonNull(probes, "对象存储来源探测用例不能为空");
        this.synchronizations = Objects.requireNonNull(synchronizations, "对象存储同步用例不能为空");
        this.publications = Objects.requireNonNull(publications, "对象存储同步产物发布用例不能为空");
    }

    /**
     * 不接收端点、桶名、对象键、对象正文或凭据；对象集合只能由部署受控范围解析器返回。
     */
    @PostMapping
    public ResponseEntity<GovernedDataSourceLifecycleResult> register(
            TrustedRequestContext context,
            @RequestParam String knowledgeSpaceId,
            @RequestParam String name,
            @RequestParam String rangeId,
            @RequestParam long selectionRevision) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registration.register(
                new RegisterObjectStorageDataSourceCommand(
                        context, knowledgeSpaceId, name, rangeId, selectionRevision)));
    }

    /** 探测登记时冻结的范围选择，不接收对象位置、正文或质量结论。 */
    @PostMapping("/{dataSourceId}/probe")
    public GovernedDataSourceLifecycleResult probe(
            TrustedRequestContext context,
            @RequestParam String knowledgeSpaceId,
            @org.springframework.web.bind.annotation.PathVariable String dataSourceId,
            @RequestParam long expectedVersion) {
        return probes.probe(new ProbeObjectStorageDataSourceCommand(
                context, knowledgeSpaceId, dataSourceId, expectedVersion));
    }

    /** 同步登记时冻结的对象集合，不接收对象位置、正文或质量结论。 */
    @PostMapping("/{dataSourceId}/synchronization")
    public GovernedDataSourceLifecycleResult synchronize(
            TrustedRequestContext context,
            @RequestParam String knowledgeSpaceId,
            @org.springframework.web.bind.annotation.PathVariable String dataSourceId,
            @RequestParam long expectedVersion) {
        return synchronizations.synchronize(new SynchronizeObjectStorageDataSourceCommand(
                context, knowledgeSpaceId, dataSourceId, expectedVersion));
    }

    /** 发布指定对象存储同步产物；质量和归属事实由受控服务重新加载。 */
    @PostMapping("/{dataSourceId}/artifacts/{artifactId}/publish")
    public GovernedDataSourceLifecycleResult publish(
            TrustedRequestContext context,
            @RequestParam String knowledgeSpaceId,
            @org.springframework.web.bind.annotation.PathVariable String dataSourceId,
            @org.springframework.web.bind.annotation.PathVariable String artifactId,
            @RequestParam long expectedVersion) {
        return publications.publish(new PublishObjectStorageSyncArtifactCommand(
                context, knowledgeSpaceId, dataSourceId, artifactId, expectedVersion));
    }
}

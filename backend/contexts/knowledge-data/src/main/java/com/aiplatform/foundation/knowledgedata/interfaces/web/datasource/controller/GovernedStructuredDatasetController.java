package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.RegisterStructuredDatasetCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.publication.PublishStructuredDatasetSyncArtifactCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.synchronization.SynchronizeStructuredDatasetCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedStructuredDatasetRegistrationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.StructuredDatasetCsvSynchronizationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.publication.StructuredDatasetSyncArtifactPublicationService;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetFieldMapping;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetLogicalType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetRowFilter;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetSensitivityLevel;
import com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.request.StructuredDatasetRegistrationRequest;
import com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.request.StructuredDatasetFieldRequest;
import com.aiplatform.foundation.security.identity.RequiresTenantFeature;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** STRUCTURED_DATASET 私有 CSV 登记入口；不接收对象位置或外部连接参数。 */
@RequiresTenantFeature("knowledgeManagement")
@RestController
@ConditionalOnBean({
    GovernedStructuredDatasetRegistrationService.class,
    StructuredDatasetCsvSynchronizationService.class,
    StructuredDatasetSyncArtifactPublicationService.class
})
@RequestMapping("/api/v1/governed-data-sources/structured-datasets")
public class GovernedStructuredDatasetController {

    private final GovernedStructuredDatasetRegistrationService registration;
    private final StructuredDatasetCsvSynchronizationService synchronizations;
    private final StructuredDatasetSyncArtifactPublicationService publications;

    public GovernedStructuredDatasetController(
            GovernedStructuredDatasetRegistrationService registration,
            StructuredDatasetCsvSynchronizationService synchronizations,
            StructuredDatasetSyncArtifactPublicationService publications) {
        this.registration = Objects.requireNonNull(registration, "结构化数据集登记用例不能为空");
        this.synchronizations = Objects.requireNonNull(synchronizations, "结构化数据集同步用例不能为空");
        this.publications = Objects.requireNonNull(publications, "结构化数据集同步产物发布用例不能为空");
    }

    /** 同步只读取已冻结的私有 CSV 与字段映射，不接受调用方提供的正文或对象位置。 */
    @PostMapping("/{dataSourceId}/synchronization")
    public GovernedDataSourceLifecycleResult synchronize(
            TrustedRequestContext context,
            @RequestParam String knowledgeSpaceId,
            @PathVariable String dataSourceId,
            @RequestParam long expectedVersion) {
        return synchronizations.synchronize(new SynchronizeStructuredDatasetCommand(
                context, knowledgeSpaceId, dataSourceId, expectedVersion));
    }

    /** 发布只选择已同步产物；质量结论和归属事实只能由受控服务重新读取。 */
    @PostMapping("/{dataSourceId}/artifacts/{artifactId}/publish")
    public GovernedDataSourceLifecycleResult publish(
            TrustedRequestContext context,
            @RequestParam String knowledgeSpaceId,
            @PathVariable String dataSourceId,
            @PathVariable String artifactId,
            @RequestParam long expectedVersion) {
        return publications.publish(new PublishStructuredDatasetSyncArtifactCommand(
                context, knowledgeSpaceId, dataSourceId, artifactId, expectedVersion));
    }

    /** 只接收私有 UTF-8 CSV 流和冻结字段映射，不接受桶、对象键、端点或凭据。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GovernedDataSourceLifecycleResult> register(
            TrustedRequestContext context,
            @RequestPart("definition") StructuredDatasetRegistrationRequest definition,
            @RequestPart("csv") MultipartFile csv) {
        Objects.requireNonNull(definition, "结构化数据集定义不能为空");
        Objects.requireNonNull(csv, "CSV 文件不能为空");
        requireUtf8Csv(csv.getContentType());
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(registration.register(
                    new RegisterStructuredDatasetCommand(
                            context,
                            definition.knowledgeSpaceId(),
                            definition.name(),
                            definition.mappingVersion(),
                            definition.contentField(),
                            definition.titleField(),
                            mappings(definition.fields()),
                            StructuredDatasetRowFilter.valueOf(definition.rowFilter()),
                            csv.getInputStream())));
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 CSV 上传内容", exception);
        }
    }

    private static List<StructuredDatasetFieldMapping> mappings(List<StructuredDatasetFieldRequest> fields) {
        return Objects.requireNonNull(fields, "字段映射不能为空").stream()
                .map(field -> new StructuredDatasetFieldMapping(
                        field.fieldName(),
                        StructuredDatasetLogicalType.valueOf(field.logicalType()),
                        StructuredDatasetSensitivityLevel.valueOf(field.sensitivityLevel())))
                .toList();
    }

    private static void requireUtf8Csv(String contentType) {
        if (contentType == null) {
            throw new IllegalArgumentException("只支持 UTF-8 CSV");
        }
        var mediaType = MediaType.parseMediaType(contentType);
        // 浏览器 multipart 常省略 charset；实际字节仍由 RFC4180 投影器以严格 UTF-8 decoder 校验。
        if (!"text".equals(mediaType.getType())
                || !"csv".equals(mediaType.getSubtype())
                || (mediaType.getCharset() != null
                        && !StandardCharsets.UTF_8.equals(mediaType.getCharset()))) {
            throw new IllegalArgumentException("只支持 UTF-8 CSV");
        }
    }
}

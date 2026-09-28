package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.RegisterFileDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.ProbeFileDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.PublishFileSyncArtifactCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.SynchronizeFileDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.GovernedDataSourceLifecycleAction;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.GovernedDataSourceLifecycleCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedFileDataSourceSummary;
import com.aiplatform.foundation.knowledgedata.application.datasource.query.ListGovernedFileDataSourcesQuery;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileDataSourceProbeService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileDataSourceSynchronizationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileSyncArtifactPublicationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedDataSourceLifecycleService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedFileDataSourceRegistrationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedFileDataSourceQueryService;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.TextContentProcessingException;
import com.aiplatform.foundation.security.identity.RequiresTenantFeature;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** FILE 受治理来源登记入口；对象位置只由平台生成。 */
@RequiresTenantFeature("knowledgeManagement")
@RestController
@ConditionalOnBean({
    GovernedFileDataSourceRegistrationService.class,
    FileDataSourceProbeService.class,
    FileDataSourceSynchronizationService.class,
    FileSyncArtifactPublicationService.class,
    GovernedDataSourceLifecycleService.class,
    GovernedFileDataSourceQueryService.class
})
@RequestMapping("/api/v1/governed-data-sources")
public class GovernedFileDataSourceController {
    private final GovernedFileDataSourceRegistrationService registration;
    private final FileDataSourceProbeService probes;
    private final FileDataSourceSynchronizationService synchronizations;
    private final FileSyncArtifactPublicationService publications;
    private final GovernedDataSourceLifecycleService lifecycle;
    private final GovernedFileDataSourceQueryService queries;

    public GovernedFileDataSourceController(
            GovernedFileDataSourceRegistrationService registration,
            FileDataSourceProbeService probes,
            FileDataSourceSynchronizationService synchronizations,
            FileSyncArtifactPublicationService publications,
            GovernedDataSourceLifecycleService lifecycle,
            GovernedFileDataSourceQueryService queries) {
        this.registration = Objects.requireNonNull(registration, "文件来源登记用例不能为空");
        this.probes = Objects.requireNonNull(probes, "文件来源探测用例不能为空");
        this.synchronizations = Objects.requireNonNull(synchronizations, "文件来源同步用例不能为空");
        this.publications = Objects.requireNonNull(publications, "同步产物发布用例不能为空");
        this.lifecycle = Objects.requireNonNull(lifecycle, "来源生命周期用例不能为空");
        this.queries = Objects.requireNonNull(queries, "FILE 来源查询用例不能为空");
    }

    /**
     * 只返回当前租户知识空间内的 FILE 来源与当前已发布产物摘要。
     *
     * <p>该入口不接收对象位置、连接参数或来源正文；权限与租户范围由可信请求上下文在应用服务中复核。</p>
     */
    @GetMapping("/files")
    public PageResult<GovernedFileDataSourceSummary> list(
            TrustedRequestContext context,
            @RequestParam String knowledgeSpaceId,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        return queries.list(new ListGovernedFileDataSourcesQuery(
                context, knowledgeSpaceId, new PageQuery(pageNumber, pageSize)));
    }

    /**
     * 只接受客户端提供的乐观版本；租户、权限和对象位置均由应用服务从可信上下文与已冻结修订取得。
     */
    @PostMapping("/files/{dataSourceId}/probe")
    public GovernedDataSourceLifecycleResult probe(
            TrustedRequestContext context,
            @RequestParam String knowledgeSpaceId,
            @PathVariable String dataSourceId,
            @RequestParam long expectedVersion) {
        return probes.probe(new ProbeFileDataSourceCommand(
                context, knowledgeSpaceId, dataSourceId, expectedVersion));
    }

    /** 同步仅使用已冻结的文件对象事实，不接受调用方提供的对象地址或内容。 */
    @PostMapping("/files/{dataSourceId}/synchronization")
    public GovernedDataSourceLifecycleResult synchronize(
            TrustedRequestContext context,
            @RequestParam String knowledgeSpaceId,
            @PathVariable String dataSourceId,
            @RequestParam long expectedVersion) {
        return synchronizations.synchronize(new SynchronizeFileDataSourceCommand(
                context, knowledgeSpaceId, dataSourceId, expectedVersion));
    }

    /** 发布仅选择已同步产物；质量结论和对象事实只能由受控服务重新读取。 */
    @PostMapping("/files/{dataSourceId}/artifacts/{artifactId}/publish")
    public GovernedDataSourceLifecycleResult publish(
            TrustedRequestContext context,
            @RequestParam String knowledgeSpaceId,
            @PathVariable String dataSourceId,
            @PathVariable String artifactId,
            @RequestParam long expectedVersion) {
        return publications.publish(new PublishFileSyncArtifactCommand(
                context, knowledgeSpaceId, dataSourceId, artifactId, expectedVersion));
    }

    /** 退役只改变受治理来源身份，不删除冻结对象、同步产物或质量审计事实。 */
    @PostMapping("/files/{dataSourceId}/retire")
    public GovernedDataSourceLifecycleResult retire(
            TrustedRequestContext context,
            @RequestParam String knowledgeSpaceId,
            @PathVariable String dataSourceId,
            @RequestParam long expectedVersion) {
        return lifecycle.transition(new GovernedDataSourceLifecycleCommand(
                context,
                knowledgeSpaceId,
                dataSourceId,
                expectedVersion,
                GovernedDataSourceLifecycleAction.RETIRE));
    }
    @PostMapping(value = "/files", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<GovernedDataSourceLifecycleResult> register(
            TrustedRequestContext context, @RequestParam String knowledgeSpaceId, @RequestParam String name,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String mediaType,
            HttpServletRequest request) {
        requireUtf8Text(mediaType);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(registration.register(
                    new RegisterFileDataSourceCommand(context, knowledgeSpaceId, name, request.getInputStream())));
        } catch (IOException exception) { throw new TextContentProcessingException(exception); }
    }
    private static void requireUtf8Text(String value) {
        if (value == null) throw new IllegalArgumentException("必须提供媒体类型");
        var parsed = MediaType.parseMediaType(value);
        if (!MediaType.TEXT_PLAIN.includes(parsed) || !StandardCharsets.UTF_8.equals(parsed.getCharset())
                || parsed.getParameters().size() != 1) throw new IllegalArgumentException("只支持 UTF-8 纯文本");
    }
}

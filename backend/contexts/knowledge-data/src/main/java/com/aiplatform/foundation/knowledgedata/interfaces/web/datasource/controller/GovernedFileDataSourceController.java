package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.RegisterFileDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.ProbeFileDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.SynchronizeFileDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.GovernedDataSourceLifecycleAction;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.GovernedDataSourceLifecycleCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileDataSourceProbeService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.FileDataSourceSynchronizationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedDataSourceLifecycleService;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.GovernedFileDataSourceRegistrationService;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.TextContentProcessingException;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** FILE 受治理来源登记入口；对象位置只由平台生成。 */
@RestController
@ConditionalOnBean({
    GovernedFileDataSourceRegistrationService.class,
    FileDataSourceProbeService.class,
    FileDataSourceSynchronizationService.class,
    GovernedDataSourceLifecycleService.class
})
@RequestMapping("/api/v1/governed-data-sources")
public class GovernedFileDataSourceController {
    private final GovernedFileDataSourceRegistrationService registration;
    private final FileDataSourceProbeService probes;
    private final FileDataSourceSynchronizationService synchronizations;
    private final GovernedDataSourceLifecycleService lifecycle;

    public GovernedFileDataSourceController(
            GovernedFileDataSourceRegistrationService registration,
            FileDataSourceProbeService probes,
            FileDataSourceSynchronizationService synchronizations,
            GovernedDataSourceLifecycleService lifecycle) {
        this.registration = Objects.requireNonNull(registration, "文件来源登记用例不能为空");
        this.probes = Objects.requireNonNull(probes, "文件来源探测用例不能为空");
        this.synchronizations = Objects.requireNonNull(synchronizations, "文件来源同步用例不能为空");
        this.lifecycle = Objects.requireNonNull(lifecycle, "来源生命周期用例不能为空");
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

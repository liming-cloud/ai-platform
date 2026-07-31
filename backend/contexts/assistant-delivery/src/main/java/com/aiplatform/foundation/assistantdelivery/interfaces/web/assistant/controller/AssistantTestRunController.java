package com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.controller;

import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.command.RunAssistantDraftTestCommand;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.dto.AssistantTestRunResult;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.service.AssistantTestRunService;
import com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.request.AssistantDraftTestRequest;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 助手负责人发起草稿可信测试的受控入口。
 */
@RestController
@RequestMapping("/api/v1/assistant-versions")
@ConditionalOnBean(AssistantTestRunService.class)
public class AssistantTestRunController {

    private final AssistantTestRunService tests;

    public AssistantTestRunController(AssistantTestRunService tests) {
        this.tests = Objects.requireNonNull(tests, "助手草稿测试用例不能为空");
    }

    /**
     * 只接受当前问题；可信上下文由参数解析器从令牌恢复。
     */
    @PostMapping("/{assistantVersionId}/test-runs")
    public ResponseEntity<AssistantTestRunResult> run(
            TrustedRequestContext context,
            @PathVariable String assistantVersionId,
            @RequestBody AssistantDraftTestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tests.run(
                new RunAssistantDraftTestCommand(context, assistantVersionId, request.question())));
    }
}

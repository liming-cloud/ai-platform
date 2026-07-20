package com.aiplatform.appstudio.domain.port.chat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.appstudio.domain.Citation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatCompletionResultTest {
    @Test
    void rejectsMissingAnswerInsteadOfEmptyFallback() {
        assertThatThrownBy(() -> new ChatCompletionResult(null, List.of(citation()), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("回答内容不能为空");

        assertThatThrownBy(() -> new ChatCompletionResult(" ", List.of(citation()), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("回答内容不能为空");
    }

    @Test
    void rejectsAnswerWithoutCitationInsteadOfLeakingUntraceableAnswer() {
        assertThatThrownBy(() -> new ChatCompletionResult("企业制度回答", List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("回答结果必须携带引用列表");
    }

    @Test
    void normalizesAnswerAndAuditAttributesBeforeAuditUse() {
        ChatCompletionResult result = new ChatCompletionResult(
                " 企业制度回答 ",
                List.of(citation()),
                " 88 ",
                Map.of(" traceId ", " trace-1 ")
        );

        assertThat(result.answer()).isEqualTo("企业制度回答");
        assertThat(result.modelInvocationLogId()).isEqualTo("88");
        assertThat(result.auditAttributes()).containsEntry("traceId", "trace-1");
    }

    @Test
    void failedResultCarriesInvocationDiagnosticsWithoutSecretLeakage() {
        ChatCompletionResult result = ChatCompletionResult.failed(
                " 99 ",
                "IllegalStateException",
                "simulated model outage with apiKey=***"
        );

        assertThat(result.answerable()).isFalse();
        assertThat(result.answer()).isEqualTo("模型调用失败，已记录调用日志，请在调试运行详情中查看错误诊断。");
        assertThat(result.citations()).isEmpty();
        assertThat(result.modelInvocationLogId()).isEqualTo("99");
        assertThat(result.failureReason()).isEqualTo("模型调用失败，已记录调用日志，请在调试运行详情中查看错误诊断。");
        assertThat(result.auditAttributes())
                .containsEntry("invocationStatus", "FAILED")
                .containsEntry("decisionSource", "model-invocation-failed")
                .containsEntry("errorCode", "IllegalStateException");
        assertThat(result.auditAttributes().get("errorMessage"))
                .contains("simulated model outage")
                .doesNotContain("***")
                .doesNotContain("apiKey=");
    }

    @Test
    void rejectsNullCitationAndBlankAuditAttributeInsteadOfLeakingDamagedResultFacts() {
        List<Citation> citations = new ArrayList<>();
        citations.add(null);

        assertThatThrownBy(() -> new ChatCompletionResult("企业制度回答", citations, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("引用列表不能为空");

        assertThatThrownBy(() -> new ChatCompletionResult("企业制度回答", List.of(citation()), Map.of(" traceId ", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审计属性 value不能为空");
    }

    private static Citation citation() {
        return new Citation("kb-1", "doc-1", "chunk-1", "制度手册", "片段", "p1", 0.9);
    }
}

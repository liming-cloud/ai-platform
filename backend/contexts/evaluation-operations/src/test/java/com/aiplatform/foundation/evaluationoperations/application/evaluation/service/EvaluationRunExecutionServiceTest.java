package com.aiplatform.foundation.evaluationoperations.application.evaluation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.assistantdelivery.application.evaluation.facade.EvaluationAssistantExecution;
import com.aiplatform.foundation.assistantdelivery.application.evaluation.facade.EvaluationAssistantExecutionResult;
import com.aiplatform.foundation.assistantdelivery.application.evaluation.facade.EvaluationAssistantTarget;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetPrivateContentStore;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationRunExecutionRequest;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDataset;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetContentClassification;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRun;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunStatus;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.policy.QualityGatePolicy;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationDatasetRepository;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository.EvaluationRunRepository;
import com.aiplatform.foundation.evaluationoperations.infrastructure.staging.JsonLinesEvaluationDatasetEntryReader;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EvaluationRunExecutionServiceTest {

    private static final TenantId TENANT_ID = new TenantId(100000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");
    private static final String EVIDENCE_ID = "a".repeat(64);

    @Test
    void 应逐条执行私有条目并仅提交无正文的运行事实() {
        var contents = new RecordingContents(entries());
        var saved = new AtomicReference<EvaluationRun>();
        var executions = new AtomicInteger();
        var service = service(contents, saved, executions, false);

        var run = service.execute(context("assistant:write"),
                new EvaluationRunExecutionRequest(100000000000000010L, 100000000000000020L));

        assertEquals(run, saved.get());
        assertEquals(2, executions.get());
        assertEquals(1, run.observation().expectedEvidenceCount());
        assertEquals(1, run.observation().correctRefusalCount());
        assertEquals(1D, run.metrics().recallAt10());
        assertTrue(run.metrics().computable());
    }

    @Test
    void 无助手写权限时不得读取私有内容或调用执行门面() {
        var contents = new RecordingContents(entries());
        var saved = new AtomicReference<EvaluationRun>();
        var executions = new AtomicInteger();
        var service = service(contents, saved, executions, false);

        assertThrows(IllegalArgumentException.class, () -> service.execute(context("assistant:query"),
                new EvaluationRunExecutionRequest(100000000000000010L, 100000000000000020L)));

        assertEquals(0, contents.opened.get());
        assertEquals(0, executions.get());
        assertTrue(saved.get() == null);
    }

    @Test
    void 未知但格式正确的引用稳定标识必须使运行失败关闭() {
        var contents = new RecordingContents(entries());
        var saved = new AtomicReference<EvaluationRun>();
        var executions = new AtomicInteger();
        var service = service(contents, saved, executions, true);

        var run = service.execute(context("assistant:write"),
                new EvaluationRunExecutionRequest(100000000000000010L, 100000000000000020L));

        assertEquals(EvaluationRunStatus.FAILED, run.status());
        assertTrue(run.observation().unauthorizedEvidence());
    }

    private static EvaluationRunExecutionService service(
            RecordingContents contents,
            AtomicReference<EvaluationRun> saved,
            AtomicInteger executions,
            boolean unknownCitation) {
        EvaluationDatasetRepository datasets = (tenantId, id) -> Optional.of(dataset());
        EvaluationRunRepository runs = saved::set;
        EntityIdGenerator identifiers = () -> 100000000000000030L;
        EvaluationAssistantExecution assistant = new EvaluationAssistantExecution() {
            @Override
            public Optional<EvaluationAssistantTarget> freeze(TrustedRequestContext context, long assistantVersionId) {
                return Optional.of(new EvaluationAssistantTarget(
                        assistantVersionId,
                        7L,
                        "b".repeat(64)));
            }

            @Override
            public EvaluationAssistantExecutionResult execute(
                    TrustedRequestContext context,
                    EvaluationAssistantTarget target,
                    String input) {
                executions.incrementAndGet();
                if (input.equals("需要证据的合成问题")) {
                    return new EvaluationAssistantExecutionResult(
                            EvaluationAssistantExecutionResult.Outcome.ANSWERED,
                            1,
                            List.of(EVIDENCE_ID),
                            List.of(unknownCitation ? "b".repeat(64) : EVIDENCE_ID));
                }
                return new EvaluationAssistantExecutionResult(
                        EvaluationAssistantExecutionResult.Outcome.REFUSED_INSUFFICIENT_EVIDENCE,
                        0,
                        List.of(),
                        List.of());
            }
        };
        return new EvaluationRunExecutionService(
                datasets,
                runs,
                contents,
                assistant,
                new JsonLinesEvaluationDatasetEntryReader(),
                QualityGatePolicy.qualityGateV1(),
                identifiers,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static EvaluationDataset dataset() {
        return EvaluationDataset.create(
                        TENANT_ID,
                        new EvaluationDatasetId(100000000000000010L),
                        "private://evaluation/dataset-10",
                        "c".repeat(64),
                        2,
                        EvaluationDatasetContentClassification.SYNTHETIC,
                        "evaluation-retention-v1",
                        NOW.minusSeconds(60))
                .publish();
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                TENANT_ID,
                new SubjectId(100000000000000002L),
                new MembershipId(100000000000000003L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                Set.of(new PermissionCode(permission)),
                "trc_evaluation_execution");
    }

    private static String entries() {
        return """
                {"input":"需要证据的合成问题","expectedEvidenceStableIds":["%s"],"expectedOutcome":"ANSWER"}
                {"input":"应拒答的合成问题","expectedEvidenceStableIds":[],"expectedOutcome":"REFUSE"}
                """.formatted(EVIDENCE_ID);
    }

    private static final class RecordingContents implements EvaluationDatasetPrivateContentStore {

        private final String entries;
        private final AtomicInteger opened = new AtomicInteger();

        private RecordingContents(String entries) {
            this.entries = entries;
        }

        @Override
        public String put(TenantId tenantId, long datasetId,
                com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetRegistrationContent content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream open(String privateContentReference) {
            opened.incrementAndGet();
            return new ByteArrayInputStream(entries.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void delete(String privateContentReference) {
            throw new UnsupportedOperationException();
        }
    }
}

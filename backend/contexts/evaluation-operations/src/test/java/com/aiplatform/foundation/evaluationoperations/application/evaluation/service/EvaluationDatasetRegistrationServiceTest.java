package com.aiplatform.foundation.evaluationoperations.application.evaluation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetPrivateContentStore;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetContentStagingPort;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetRegistrationStore;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.StagedEvaluationDatasetContent;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetRegistrationContent;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetRegistrationRequest;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDataset;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EvaluationDatasetRegistrationServiceTest {

    private static final TenantId TENANT_ID = new TenantId(100000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-05T09:00:00Z");

    @Test
    void 无助手写权限时不得写入私有内容或提交数据集() {
        var storage = new RecordingStorage();
        var committed = new AtomicReference<EvaluationDataset>();
        var service = service(storage, committed, false);

        assertThrows(IllegalArgumentException.class, () -> service.register(context("assistant:query"), request()));

        assertTrue(storage.reference.get() == null);
        assertTrue(committed.get() == null);
    }

    @Test
    void 应以令牌租户登记合成私有内容并只提交引用元数据() {
        var storage = new RecordingStorage();
        var committed = new AtomicReference<EvaluationDataset>();
        var service = service(storage, committed, false);

        var dataset = service.register(context("assistant:write"), request());

        assertEquals(dataset, committed.get());
        assertEquals(TENANT_ID, dataset.tenantId());
        assertEquals("private://evaluation/dataset-100000000000000010", storage.reference.get());
        assertEquals("b".repeat(64), storage.contentDigest.get());
        assertEquals(2, storage.entryCount.get());
    }

    @Test
    void 元数据提交失败时必须删除已写入的私有对象() {
        var storage = new RecordingStorage();
        var service = service(storage, new AtomicReference<>(), true);

        assertThrows(IllegalStateException.class, () -> service.register(context("assistant:write"), request()));

        assertEquals(storage.reference.get(), storage.deleted.get());
    }

    private static EvaluationDatasetRegistrationService service(
            RecordingStorage storage,
            AtomicReference<EvaluationDataset> committed,
            boolean failCommit) {
        EvaluationDatasetRegistrationStore datasets = dataset -> {
            if (failCommit) {
                throw new IllegalStateException("提交失败");
            }
            committed.set(dataset);
        };
        EntityIdGenerator identifiers = () -> 100000000000000010L;
        return new EvaluationDatasetRegistrationService(
                datasets,
                storage,
                stagedContents(),
                identifiers,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static EvaluationDatasetRegistrationRequest request() {
        return new EvaluationDatasetRegistrationRequest(
                new ByteArrayInputStream(new byte[] {1}),
                "evaluation-retention-v1");
    }

    private static EvaluationDatasetContentStagingPort stagedContents() {
        return input -> new StagedEvaluationDatasetContent() {
            @Override
            public ByteArrayInputStream openStream() {
                return new ByteArrayInputStream(new byte[] {1});
            }

            @Override
            public String contentDigest() {
                return "b".repeat(64);
            }

            @Override
            public int entryCount() {
                return 2;
            }

            @Override
            public void close() {
            }
        };
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                TENANT_ID,
                new SubjectId(100000000000000002L),
                new com.aiplatform.foundation.security.identity.MembershipId(100000000000000003L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                Set.of(new PermissionCode(permission)),
                "trc_evaluation_registration");
    }

    private static final class RecordingStorage implements EvaluationDatasetPrivateContentStore {

        private final AtomicReference<String> reference = new AtomicReference<>();
        private final AtomicReference<String> deleted = new AtomicReference<>();
        private final AtomicReference<String> contentDigest = new AtomicReference<>();
        private final AtomicReference<Integer> entryCount = new AtomicReference<>();

        @Override
        public String put(TenantId tenantId, long datasetId, EvaluationDatasetRegistrationContent content) {
            var value = "private://evaluation/dataset-" + datasetId;
            reference.set(value);
            contentDigest.set(content.contentDigest());
            entryCount.set(content.entryCount());
            return value;
        }

        @Override
        public void delete(String privateContentReference) {
            deleted.set(privateContentReference);
        }
    }
}

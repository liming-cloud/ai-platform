package com.aiplatform.foundation.knowledgedata.application.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.api.model.CreateKnowledgeSpaceCommand;
import com.aiplatform.foundation.knowledgedata.api.model.ListKnowledgeSpacesQuery;
import com.aiplatform.foundation.knowledgedata.application.port.out.KnowledgeSpaceCreation;
import com.aiplatform.foundation.knowledgedata.application.port.out.KnowledgeSpaceCreationOutcome;
import com.aiplatform.foundation.knowledgedata.application.port.out.KnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.application.port.out.KnowledgeSpaceSlice;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpace;
import com.aiplatform.foundation.organizationaccess.api.model.AccessChannel;
import com.aiplatform.foundation.organizationaccess.api.model.CredentialType;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultKnowledgeSpaceServiceTest {

    private static final TenantId TENANT_ID =
            new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final MembershipId MEMBERSHIP_ID =
            new MembershipId(4921825928971026434L);
    private static final UUID SPACE_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void 有写权限时使用可信租户创建知识空间() {
        var repository = new RecordingRepository();
        var service = new DefaultKnowledgeSpaceService(repository, CLOCK, () -> SPACE_ID, 3);

        var result = service.create(new CreateKnowledgeSpaceCommand(
                context("knowledge:write"),
                "request-00000001",
                "门店商品知识",
                "供门店机器人使用"));

        assertEquals(SPACE_ID, result.knowledgeSpace().id());
        assertEquals(TENANT_ID, repository.creation.space().tenantId());
        assertEquals(MEMBERSHIP_ID, repository.creation.space().createdBy());
        assertEquals(3, repository.tenantLimit);
    }

    @Test
    void 缺少写权限时在访问仓储前失败关闭() {
        var repository = new RecordingRepository();
        var service = new DefaultKnowledgeSpaceService(repository, CLOCK, () -> SPACE_ID, 3);

        assertThrows(KnowledgePermissionDeniedException.class, () -> service.create(
                new CreateKnowledgeSpaceCommand(
                        context("knowledge:read"),
                        "request-00000001",
                        "门店商品知识",
                        null)));
        assertTrue(repository.creation == null);
    }

    @Test
    void 列表只使用可信上下文中的租户() {
        var repository = new RecordingRepository();
        var service = new DefaultKnowledgeSpaceService(repository, CLOCK, () -> SPACE_ID, 3);

        var page = service.list(new ListKnowledgeSpacesQuery(
                context("knowledge:read"),
                null,
                20));

        assertEquals(TENANT_ID, repository.listTenant);
        assertTrue(page.items().isEmpty());
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                TENANT_ID,
                new SubjectId(1843651857942052866L),
                MEMBERSHIP_ID,
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode(permission)),
                "trace-0001");
    }

    private static final class RecordingRepository implements KnowledgeSpaceRepository {

        private KnowledgeSpaceCreation creation;
        private int tenantLimit;
        private TenantId listTenant;

        @Override
        public KnowledgeSpaceCreationOutcome create(
                KnowledgeSpaceCreation creation,
                int tenantLimit) {
            this.creation = creation;
            this.tenantLimit = tenantLimit;
            return new KnowledgeSpaceCreationOutcome(creation.space(), false);
        }

        @Override
        public KnowledgeSpaceSlice findPage(TenantId tenantId, String cursor, int limit) {
            listTenant = tenantId;
            return new KnowledgeSpaceSlice(List.of(), null);
        }
    }
}

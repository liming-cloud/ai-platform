package com.aiplatform.foundation.organizationaccess.application.member.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionSnapshot;
import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberAccountFact;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccessFacts;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.domain.member.model.ConversationFeatures;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
import com.aiplatform.foundation.security.identity.*;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** 使用真实有效权限服务，模拟的仅是公开持久化／发布端口。 */
class MemberEffectiveAccessServiceTest {
    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final TrustedRequestContext CONTEXT = new TrustedRequestContext(TENANT, new SubjectId(2001),
            new MembershipId(3001), CredentialType.PERSON, AccessChannel.CONSOLE, 7, Set.of(new PermissionCode("assistant:query")), "test-member-access");
    private final AtomicReference<MemberAccountFact> current = new AtomicReference<>(fact("TENANT_USER", "ENABLED", true, 7, true,
            Map.of("assistantSelection", true, "conversation", true, "ownHistory", false, "authorizedCitations", false), Set.of()));
    private final TenantFeatureSettingsStore ceilings = mock(TenantFeatureSettingsStore.class);
    private final PublishedAssistantVersionLookup published = mock(PublishedAssistantVersionLookup.class);
    private final MemberAccessFacts facts = (tenant, id) -> tenant.equals(TENANT) && id == 3001 ? Optional.ofNullable(current.get()) : Optional.empty();
    private final MemberEffectiveAccessService service = new MemberEffectiveAccessService(facts, ceilings, published);

    MemberEffectiveAccessServiceTest() { when(ceilings.find(TENANT.value())).thenReturn(Optional.of(TenantFeatureSettings.defaults(TENANT))); }

    @Test
    void 无助手不等于全部助手且历史关闭不关闭新的有权对话() {
        var snapshot = mock(PublishedAssistantVersionSnapshot.class);
        when(snapshot.assistantId()).thenReturn(4001L);
        when(published.find(TENANT, 5001)).thenReturn(Optional.of(snapshot));
        assertTrue(service.read(CONTEXT).allows("conversation"));
        assertFalse(service.read(CONTEXT).allows("ownHistory"));
        assertThrows(ApplicationException.class, () -> service.requireAssistantVersion(CONTEXT, 5001, "conversation"));
        current.set(fact("TENANT_USER", "ENABLED", true, 7, true, current.get().features().values(), Set.of(4001L)));
        assertDoesNotThrow(() -> service.requireAssistantVersion(CONTEXT, 5001, "conversation"));
        assertThrows(ApplicationException.class, () -> service.requireAssistantVersion(CONTEXT, 5001, "ownHistory"));
        when(published.find(TENANT, 5001)).thenReturn(Optional.empty());
        assertThrows(ApplicationException.class, () -> service.requireAssistantVersion(CONTEXT, 5001, "conversation"));
    }

    @Test
    void 当前租户上限即时收紧且不改写成员已保存值() {
        var values = new EnumMap<>(TenantFeatureSettings.defaults(TENANT).availableFeatures());
        values.put(TenantFeature.CONVERSATION, false);
        when(ceilings.find(TENANT.value())).thenReturn(Optional.of(new TenantFeatureSettings(TENANT, values, 1)));
        assertFalse(service.read(CONTEXT).allows("conversation"));
        assertTrue(current.get().features().values().get("conversation"));
        assertThrows(ApplicationException.class, () -> service.requireFeature(CONTEXT, "conversation"));
    }

    @Test
    void 资格身份固定角色或权限版本失效时拒绝旧会话() {
        var flags = current.get().features().values();
        for (var invalid : java.util.List.of(
                fact("TENANT_USER", "DISABLED", true, 7, true, flags, Set.of()),
                fact("TENANT_USER", "ENABLED", false, 7, true, flags, Set.of()),
                fact("TENANT_USER", "ENABLED", true, 8, true, flags, Set.of()),
                fact("TENANT_USER", "ENABLED", true, 7, false, flags, Set.of()),
                fact("UNKNOWN", "ENABLED", true, 7, true, flags, Set.of()))) {
            current.set(invalid);
            assertThrows(ApplicationException.class, () -> service.read(CONTEXT));
        }
        current.set(null);
        assertThrows(ApplicationException.class, () -> service.read(CONTEXT));
    }

    @Test
    void 普通成员缺少功能事实不得补默认授权且缺少租户上限也失败关闭() {
        current.set(fact("TENANT_USER", "ENABLED", true, 7, true, null, Set.of()));
        assertThrows(ApplicationException.class, () -> service.read(CONTEXT));
        current.set(fact("TENANT_ADMINISTRATOR", "ENABLED", true, 7, true, null, Set.of()));
        assertTrue(service.read(CONTEXT).tenantAdministrator());
        when(ceilings.find(TENANT.value())).thenReturn(Optional.empty());
        assertThrows(ApplicationException.class, () -> service.read(CONTEXT));
    }

    private static MemberAccountFact fact(String role, String status, boolean active, long permissionVersion,
            boolean ready, Map<String, Boolean> features, Set<Long> assistants) {
        return new MemberAccountFact(3001, 2001, "member-test", role, status, active, 0, 0,
                permissionVersion, Instant.EPOCH, features == null ? null : new ConversationFeatures(features), 0, assistants, true, ready);
    }
}

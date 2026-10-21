package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleReadMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PostgresTenantActivityFactsTest {
    @Test void 当前活动投影不使用共享锁且无效ID不查询() {
        var mapper = mock(TenantLifecycleReadMapper.class);
        when(mapper.isActive(201L)).thenReturn(true);
        var owner = new PostgresTenantActivityFacts(mapper);
        assertTrue(owner.isActive(201L)); assertFalse(owner.isActive(202L)); assertFalse(owner.isActive(-1));
        verify(mapper, never()).lockActive(anyLong()); verify(mapper, never()).isActive(-1);
    }

    @Test void 缺写事务或只读事务不能申请活动事实锁() {
        var mapper = mock(TenantLifecycleReadMapper.class);
        var owner = new PostgresTenantActivityFacts(mapper);
        assertThrows(IllegalStateException.class, () -> owner.requireActive(Set.of(201L)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        try { assertThrows(IllegalStateException.class, () -> owner.requireActive(Set.of(201L))); }
        finally { TransactionSynchronizationManager.clear(); }
        verifyNoInteractions(mapper);
    }

    @Test void 写事务按稳定租户顺序复核且任一失效即拒绝() {
        var mapper = mock(TenantLifecycleReadMapper.class);
        when(mapper.lockActive(201L)).thenReturn(201L);
        when(mapper.lockActive(202L)).thenReturn(null);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalArgumentException.class, () -> new PostgresTenantActivityFacts(mapper).requireActive(Set.of(202L, 201L)));
        } finally { TransactionSynchronizationManager.clear(); }
        var order = inOrder(mapper); order.verify(mapper).lockActive(201L); order.verify(mapper).lockActive(202L);
    }
}

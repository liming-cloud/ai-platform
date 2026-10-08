package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccountMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Objects;
import org.springframework.transaction.support.TransactionTemplate;

/** 只处理迁移明确标记的普通资格；撤销失败保留标记并阻止应用接收请求。 */
public final class MemberAccessCutover {
    private final MemberAccountMapper mapper;
    private final TenantMemberSessionRevoker sessions;
    private final TransactionTemplate transactions;

    /** 复用正式会话撤销端口，禁止清空 Redis 或恢复默认授权。 */
    public MemberAccessCutover(MemberAccountMapper mapper, TenantMemberSessionRevoker sessions, TransactionTemplate transactions) {
        this.mapper = Objects.requireNonNull(mapper);
        this.sessions = Objects.requireNonNull(sessions);
        this.transactions = Objects.requireNonNull(transactions);
    }

    /** 每批只读取有限资格；标记提交后再读取第一页，重启可安全重试。 */
    public void run() {
        while (Boolean.TRUE.equals(transactions.execute(status -> {
            var pending = mapper.pendingRevocations(new Page<Long>(1, 100, false)).getRecords();
            for (var id : pending) {
                sessions.revokeTenantSessions(new MemberId(id));
                mapper.markRevoked(id);
            }
            return !pending.isEmpty();
        }))) {
            // 只处理尚未收口的批次，不扫描或记录已撤销会话值。
        }
    }
}

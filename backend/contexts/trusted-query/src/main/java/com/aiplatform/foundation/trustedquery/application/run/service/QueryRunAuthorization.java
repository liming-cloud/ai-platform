package com.aiplatform.foundation.trustedquery.application.run.service;

import com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.application.candidate.exception.CandidateSearchPermissionDeniedException;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRun;
import java.util.Objects;

/** 查询入口与输出共用当前组织事实；历史关闭不阻止新对话，旧事实不能恢复撤权。 */
public final class QueryRunAuthorization {
    private final MemberAccessFacade access;

    /** 必须注入正式组织 facade；没有授权事实时不能启动查询服务。 */
    public QueryRunAuthorization(MemberAccessFacade access) { this.access = Objects.requireNonNull(access); }

    /** 新对话以及模型完成后的输出均重新检查当前助手和功能。 */
    public void requireNew(TrustedRequestContext context, long assistantVersionId) {
        access.requireAssistantVersion(context, assistantVersionId, "conversation");
    }

    /** 读取或续传只允许本人的运行，并重新核对当前助手及历史功能。 */
    public void requireHistory(TrustedRequestContext context, QueryRun run) {
        if (run.requestedBy().value() != context.membershipId().value()) {
            throw new CandidateSearchPermissionDeniedException();
        }
        access.requireAssistantVersion(context, run.assistantVersionId(), "ownHistory");
    }

    /** 引用是独立的有效功能；关闭时不输出来源正文或标识。 */
    public boolean citations(TrustedRequestContext context) { return access.read(context).allows("authorizedCitations"); }

    /** 旧知识空间检索没有助手范围，只保留管理员能力，普通成员必须走受管助手。 */
    public void requireAdministrativeSearch(TrustedRequestContext context) {
        if (!access.read(context).tenantAdministrator()) throw new CandidateSearchPermissionDeniedException();
    }
}

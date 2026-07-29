package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedFileDataSourceSummary;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.GovernedFileDataSourceReadPort;
import com.aiplatform.foundation.knowledgedata.application.datasource.query.ListGovernedFileDataSourcesQuery;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;

/** 在可信读取权限边界内查询可恢复的 FILE 来源任务摘要。 */
public final class GovernedFileDataSourceQueryService {

    private static final PermissionCode READ = new PermissionCode("knowledge:read");

    private final GovernedFileDataSourceReadPort reads;

    public GovernedFileDataSourceQueryService(GovernedFileDataSourceReadPort reads) {
        this.reads = Objects.requireNonNull(reads, "FILE 来源读取端口不能为空");
    }

    /**
     * 返回当前租户、知识空间内的 FILE 来源及其最新已发布产物摘要。
     *
     * @throws KnowledgePermissionDeniedException 当前成员没有知识读取权限时拒绝，且不访问读取端口。
     */
    public PageResult<GovernedFileDataSourceSummary> list(
            ListGovernedFileDataSourcesQuery query) {
        Objects.requireNonNull(query, "FILE 来源列表查询不能为空");
        if (!query.context().hasPermission(READ)) {
            throw new KnowledgePermissionDeniedException();
        }
        return reads.list(
                query.context().tenantId(),
                KnowledgeSpaceId.parse(query.knowledgeSpaceId()),
                query.page());
    }
}

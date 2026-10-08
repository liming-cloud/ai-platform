package com.aiplatform.foundation.organizationaccess.application.accesspolicy;

import com.aiplatform.foundation.organizationaccess.domain.accesspolicy.AccessPolicyVersion;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.List;
import java.util.Optional;

/** 策略、版本与非秘密操作审计同业务事务持久化；不保存第二套有效 ACL。 */
public interface AccessPolicyStore {
    /** 名称归当前租户所有，由数据库唯一约束裁决同名创建。 */
    record Policy(long id, String name) { }
    /** 目录只投影最新版本，不能将旧发布状态混入草稿状态。 */
    record Summary(String policyId, String name, String assistantName, String status, String versionId, int number) { }
    /** 浏览器只获得普通资格及登录名，不获得全局主体标识。 */
    record MemberChoice(String membershipId, String loginName) { }
    /** 在服务端筛选可用普通成员并计算真实总数。 */
    PageResult<MemberChoice> memberChoices(TenantId tenant, PageQuery page, String q);
    /** 最新版本目录在数据库完成名称、范围及状态筛选后分页。 */
    PageResult<Summary> list(TenantId tenant, PageQuery page, String q, String status);
    /** 精确读取本租户策略，不泄露跨租户对象存在性。 */
    Optional<Policy> find(TenantId tenant, long policyId);
    /** 在调用方事务插入唯一名称，失败不能留下版本或授权。 */
    void create(TenantId tenant, Policy policy);
    /** 同时匹配租户、策略及版本，防止路径标识拼接越权。 */
    Optional<AccessPolicyVersion> version(TenantId tenant, long policyId, long versionId);
    /** 返回最大版本号，不将当前发布强行当作最新草稿。 */
    Optional<AccessPolicyVersion> latest(TenantId tenant, long policyId);
    /** 读取本策略唯一的当前发布；历史替代版本不能重放。 */
    Optional<AccessPolicyVersion> published(TenantId tenant, long policyId);
    /** 版本正文按版本号倒序分页，保持历史可追溯。 */
    PageResult<AccessPolicyVersion> versions(TenantId tenant, long policyId, PageQuery page);
    /** 正文及候选成员同事务插入，不写有效助手关系。 */
    void insert(TenantId tenant, AccessPolicyVersion version);
    /** 只更新匹配原修订号的版本；并发失败由外层事务回滚。 */
    void update(TenantId tenant, AccessPolicyVersion version, long expectedRevision);
    /** 内部批次逐页读取普通资格，不向浏览器泄露主体。 */
    List<Long> ordinaryMembers(TenantId tenant, PageQuery page);
    /** 仅记录动作及版本，审计失败必须使业务事务失败。 */
    void audit(TenantId tenant, long actor, long policyId, long versionId, String action, long revision);
}

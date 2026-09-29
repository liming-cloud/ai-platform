package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.util.Optional;

/** 平台账户治理所需的锁定读取与乐观写入端口。 */
public interface PlatformAccountStore extends PlatformAccountDirectory {

    /** 停用管理员前串行化最后管理员判断，避免不同目标并发停用后留下零名可用管理员。 */
    void lockAdministratorDisableGate();

    Optional<SubjectIdentity> lock(SubjectId subjectId);

    boolean hasActiveAccountAdministratorRole(SubjectId subjectId);

    long countOtherActiveAccountAdministrators(SubjectId subjectId);

    /**
     * 插入全局账户事实。
     *
     * @return 当且仅当数据库全局登录名唯一约束允许本次写入时为真。
     */
    boolean insert(SubjectIdentity identity);

    void update(SubjectIdentity identity);

    /** 账户事实和成功审计必须由同一事务提交；失败请求不记作成功。 */
    void recordOperation(long operatorSubjectId, long targetSubjectId, String operation, long targetVersion);
}

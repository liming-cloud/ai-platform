package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.util.Objects;

/**
 * 平台账户高风险操作的纯领域守卫。
 *
 * <p>本服务只计算下一账户事实和应撤销的全局主体，应用层负责授权、锁定读取、原子提交与会话撤销。</p>
 */
public final class PlatformAccountGovernance {

    private PlatformAccountGovernance() {
    }

    /**
     * 禁用非自身且不是最后账户管理员的目标账户。
     *
     * @throws IllegalArgumentException 当操作者与目标相同或输入为空时拒绝。
     * @throws IllegalStateException 当操作会移除最后一个活动账户管理员时拒绝。
     */
    public static AccountOperationResult disable(
            SubjectId operatorId,
            SubjectIdentity target,
            boolean targetHasAccountAdministratorRole,
            long otherActiveAccountAdministrators) {
        operatorId = Objects.requireNonNull(operatorId, "操作者主体标识不能为空");
        target = Objects.requireNonNull(target, "目标账户不能为空");
        if (operatorId.equals(target.id())) {
            throw new IllegalArgumentException("账户管理员不能禁用自身");
        }
        if (otherActiveAccountAdministrators < 0L) {
            throw new IllegalArgumentException("其他活动账户管理员数量不能为负数");
        }
        if (target.isActive()
                && targetHasAccountAdministratorRole
                && otherActiveAccountAdministrators == 0L) {
            throw new IllegalStateException("不能禁用最后一个活动账户管理员");
        }
        return new AccountOperationResult(target.disable(), target.id());
    }

    /**
     * 用已生成的摘要重置非自身目标账户凭据。
     *
     * @throws IllegalArgumentException 当操作者与目标相同或摘要为空时拒绝。
     */
    public static AccountOperationResult resetPassword(
            SubjectId operatorId, SubjectIdentity target, EncodedPassword replacement) {
        operatorId = Objects.requireNonNull(operatorId, "操作者主体标识不能为空");
        target = Objects.requireNonNull(target, "目标账户不能为空");
        if (operatorId.equals(target.id())) {
            throw new IllegalArgumentException("账户管理员不能重置自身凭据");
        }
        return new AccountOperationResult(
                target.resetPassword(Objects.requireNonNull(replacement, "新凭据摘要不能为空")),
                target.id());
    }

    /**
     * 启用目标账户并要求撤销其所有旧范围会话。
     *
     * @throws IllegalArgumentException 当操作者与目标相同或输入为空时拒绝。
     */
    public static AccountOperationResult enable(SubjectId operatorId, SubjectIdentity target) {
        operatorId = Objects.requireNonNull(operatorId, "操作者主体标识不能为空");
        target = Objects.requireNonNull(target, "目标账户不能为空");
        if (operatorId.equals(target.id())) {
            throw new IllegalArgumentException("账户管理员不能启用自身");
        }
        return new AccountOperationResult(target.enable(), target.id());
    }

    /** 账户写入与会话撤销协作使用的不可变操作结果。 */
    public record AccountOperationResult(SubjectIdentity identity, SubjectId sessionRevocationSubjectId) {
        public AccountOperationResult {
            identity = Objects.requireNonNull(identity, "更新后的账户不能为空");
            sessionRevocationSubjectId = Objects.requireNonNull(sessionRevocationSubjectId, "会话撤销主体不能为空");
            if (!identity.id().equals(sessionRevocationSubjectId)) {
                throw new IllegalArgumentException("会话撤销主体必须与更新账户一致");
            }
        }
    }
}

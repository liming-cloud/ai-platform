package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;

/** 撤销主体的全部平台和租户会话族。 */
@FunctionalInterface
public interface PersonSessionFamilyRevoker {
    void revokeAll(SubjectId subjectId);
}
